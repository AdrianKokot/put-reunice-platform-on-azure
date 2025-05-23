package put.eunice.cms.user;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import put.eunice.cms.SearchCriteria;
import put.eunice.cms.email.EmailSending;
import put.eunice.cms.page.PageRepository;
import put.eunice.cms.resource.FileResourceRepository;
import put.eunice.cms.security.LoggedUser;
import put.eunice.cms.security.Role;
import put.eunice.cms.security.SecurityService;
import put.eunice.cms.university.University;
import put.eunice.cms.university.UniversityRepository;
import put.eunice.cms.university.exceptions.UniversityForbiddenException;
import put.eunice.cms.university.exceptions.UniversityNotFoundException;
import put.eunice.cms.user.exceptions.UserException;
import put.eunice.cms.user.exceptions.UserExceptionType;
import put.eunice.cms.user.exceptions.UserForbiddenException;
import put.eunice.cms.user.exceptions.UserNotFoundException;
import put.eunice.cms.user.projections.UserDtoDetailed;
import put.eunice.cms.user.projections.UserDtoFormCreate;
import put.eunice.cms.user.projections.UserDtoFormUpdate;
import put.eunice.cms.validation.exceptions.UnauthorizedException;
import put.eunice.cms.validation.exceptions.WrongDataStructureException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PageRepository pageRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityService securityService;
    private final EmailSending emailService;
    private final FileResourceRepository fileResourceRepository;

    public UserDtoDetailed getUser(Long id) {
        return userRepository
                .findById(id)
                .map(UserDtoDetailed::of)
                .orElseThrow(UserNotFoundException::new);
    }

    public Optional<User> getUserObjectOptional(Long id) {
        return userRepository.findById(id);
    }

    public UserDtoDetailed getLoggedUser() {
        Long id = securityService.getPrincipal().orElseThrow(UserNotFoundException::new).getId();
        return userRepository
                .findById(id)
                .map(UserDtoDetailed::of)
                .orElseThrow(UserNotFoundException::new);
    }

    @Secured("ROLE_USER")
    public Page<User> getUsers(Pageable pageable, Map<String, String> filterVars) {
        Specification<User> combinedSpecification = null;

        if (!filterVars.isEmpty()) {
            List<UserSpecification> specifications =
                    filterVars.entrySet().stream()
                            .map(
                                    entries -> {
                                        String[] filterBy = entries.getKey().split("_");

                                        return new UserSpecification(
                                                new SearchCriteria(
                                                        filterBy[0], filterBy[filterBy.length - 1], entries.getValue()));
                                    })
                            .collect(Collectors.toList());

            for (Specification<User> spec : specifications) {
                if (combinedSpecification == null) {
                    combinedSpecification = Specification.where(spec);
                }
                combinedSpecification = combinedSpecification.and(spec);
            }
        }

        return userRepository.findAll(combinedSpecification, pageable);
    }

    @Secured("ROLE_MODERATOR")
    public UserDtoDetailed createUser(UserDtoFormCreate form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new UserException(UserExceptionType.USERNAME_TAKEN, "username");
        }

        if (userRepository.existsByEmail(form.getEmail())) {
            throw new UserException(UserExceptionType.EMAIL_TAKEN, "email");
        }

        validatePassword(form.getPassword());

        User newUser = new User();

        newUser.setUsername(form.getUsername());
        if (form.getPassword() != null) {
            newUser.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        newUser.setFirstName(form.getFirstName());
        newUser.setLastName(form.getLastName());
        newUser.setEmail(form.getEmail().trim());
        newUser.setPhoneNumber(form.getPhoneNumber());
        newUser.setAccountType(form.getAccountType());
        newUser.setEnabled(form.isEnabled());

        if (!newUser.getAccountType().equals(Role.ADMIN)) {
            newUser.setEnrolledUniversities(this.validateUniversities(form.getEnrolledUniversities()));
        }

        if (!securityService.hasHigherOrEqualRoleThan(newUser.getAccountType())) {
            throw new UserForbiddenException();
        }

        LoggedUser loggedUser = securityService.getPrincipal().orElseThrow();
        if (newUser.getAccountType() != Role.ADMIN) {
            if (loggedUser.getAccountType() == Role.MODERATOR
                    && loggedUser
                            .getUniversities()
                            .toArray()
                            .equals(form.getEnrolledUniversities().toArray())) {
                throw new UserForbiddenException();
            }
        }

        emailService.sendConfirmNewAccountEmail(newUser, form.getPassword());

        return UserDtoDetailed.of(userRepository.save(newUser));
    }

    private void validatePassword(String password) {
        if (password == null) {
            password = "";
        }
        Pattern pattern = Pattern.compile("(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}");
        Matcher matcher = pattern.matcher(password);
        if (!matcher.find()) {
            throw new UserException(UserExceptionType.NOT_VALID_PASSWORD, "password");
        }
    }

    private User validateUserAndForm(Long id, UserDtoFormUpdate form) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        if (securityService.isForbiddenUser(user)) {
            throw new UserForbiddenException();
        }

        if (userRepository.existsByUsername(form.getUsername())) {
            User newUser = userRepository.findByUsername(form.getUsername()).orElse(null);
            if (!newUser.getId().equals(user.getId())) {
                throw new UserException(UserExceptionType.USERNAME_TAKEN, "username");
            }
        }

        if (userRepository.existsByEmail(form.getEmail())) {
            User newUser = userRepository.findByEmail(form.getEmail()).orElse(null);
            if (!newUser.getId().equals(user.getId())) {
                throw new UserException(UserExceptionType.EMAIL_TAKEN, "email");
            }
        }

        return user;
    }

    private boolean mainDataNotChanged(User user, UserDtoFormUpdate form) {
        return user.getUsername().equals(form.getUsername())
                && user.getFirstName().equals(form.getFirstName())
                && user.getEmail().equals(form.getEmail())
                && user.getLastName().equals(form.getLastName());
    }

    private void updateUserDetails(User user, UserDtoFormUpdate form) {
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setPhoneNumber(form.getPhoneNumber());
        user.setDescription(form.getDescription());
        user.setUsername(form.getUsername());
    }

    private void handleUpdateAccountStatus(User user, UserDtoFormUpdate form) {
        if (securityService.hasHigherRoleThan(Role.USER)) {
            if (!form.isEnabled() && user.isEnabled()) {
                emailService.sendDisableAccountEmail(
                        user, getLoggedUser().getUsername(), getLoggedUser().getEmail());
            } else if (form.isEnabled() && !user.isEnabled()) {
                emailService.sendEnableAccountEmail(
                        user, getLoggedUser().getUsername(), getLoggedUser().getEmail());
            }

            user.setEnabled(form.isEnabled());
            user.setAccountType(form.getAccountType());

            if (user.getAccountType().equals(Role.ADMIN)) {
                user.setEnrolledUniversities(new HashSet<>());
            } else {
                user.setEnrolledUniversities(this.validateUniversities(form.getEnrolledUniversities()));
            }
        }
    }

    @Secured("ROLE_USER")
    public UserDtoDetailed updateUser(Long id, UserDtoFormUpdate form) {
        User user = validateUserAndForm(id, form);
        boolean mainDataNotChanged = mainDataNotChanged(user, form);
        String oldEmail = user.getEmail();

        updateUserDetails(user, form);
        handleUpdateAccountStatus(user, form);

        if (securityService.hasHigherOrEqualRoleThan(Role.MODERATOR) && !form.getPassword().isEmpty()) {
            if (user.getAccountType() == Role.ADMIN
                    && getLoggedUser().getAccountType() == Role.MODERATOR) {
                throw new UserForbiddenException();
            } else {
                validatePassword(form.getPassword());
                user.setPassword(passwordEncoder.encode(form.getPassword()));
                emailService.sendEditUserAccountMail(
                        oldEmail,
                        user,
                        getLoggedUser().getUsername(),
                        getLoggedUser().getEmail(),
                        form.getPassword());
            }
        } else {
            if (!mainDataNotChanged) {
                emailService.sendEditUserAccountMail(
                        oldEmail, user, getLoggedUser().getUsername(), getLoggedUser().getEmail());
            }
        }
        return UserDtoDetailed.of(userRepository.save(user));
    }

    private Set<University> validateUniversities(Set<Long> universitiesId) {
        Set<University> newUniversities =
                universitiesId.stream()
                        .map(
                                universityId ->
                                        universityRepository
                                                .findById(universityId)
                                                .orElseThrow(UniversityNotFoundException::new))
                        .collect(Collectors.toSet());

        newUniversities.forEach(
                university -> {
                    if (securityService.isForbiddenUniversity(university)) {
                        throw new UniversityForbiddenException();
                    }
                });

        return newUniversities;
    }

    @Secured("ROLE_USER")
    public void modifyPasswordField(long id, Map<String, String> passwordMap) {
        if (!passwordMap.containsKey("oldPassword") || !passwordMap.containsKey("newPassword")) {
            throw new WrongDataStructureException();
        }
        String oldPassword = passwordMap.get("oldPassword");
        String newPassword = passwordMap.get("newPassword");

        validatePassword(newPassword);

        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        if (securityService.isForbiddenUser(user)) {
            throw new UserForbiddenException();
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UserException(UserExceptionType.WRONG_PASSWORD, "oldPassword");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new UserException(UserExceptionType.SAME_PASSWORD, "newPassword");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Secured("ROLE_MODERATOR")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        if (securityService.isForbiddenUserToDelete(user, true)) {
            throw new UserForbiddenException();
        }
        validateForDelete(user);
        emailService.sendDeleteAccountEmail(
                user, getLoggedUser().getUsername(), getLoggedUser().getEmail());
        userRepository.delete(user);
    }

    private void validateForDelete(User user) {
        if (pageRepository.existsByCreator(user)) {
            throw new UserException(UserExceptionType.PAGES_EXISTS);
        }

        if (fileResourceRepository.existsByAuthor(user)) {
            throw new UserException(UserExceptionType.RESOURCES_EXISTS);
        }

        if (user.isEnabled()) {
            throw new UserException(UserExceptionType.USER_IS_ENABLED);
        }
    }

    public void updateLastLoginDate(Long id) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        user.setLastLoginDate(Instant.now());
        userRepository.save(user);
    }

    @Secured("ROLE_USER")
    public UserDtoDetailed updateProfile(UserDtoFormUpdate form) {
        User user =
                validateUserAndForm(
                        securityService.getPrincipal().orElseThrow(UnauthorizedException::new).getId(), form);

        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setPhoneNumber(form.getPhoneNumber());

        return UserDtoDetailed.of(userRepository.save(user));
    }

    @Secured("ROLE_USER")
    public void changeProfilePassword(Map<String, String> passwordMap) {
        this.modifyPasswordField(
                securityService.getPrincipal().orElseThrow(UnauthorizedException::new).getId(),
                passwordMap);
    }
}
