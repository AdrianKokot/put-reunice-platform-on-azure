package put.eunice.cms.email;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import put.eunice.cms.configuration.ApplicationConfigurationProvider;
import put.eunice.cms.ticket.Ticket;
import put.eunice.cms.ticketUserStatus.TicketUserStatus;
import put.eunice.cms.user.User;

@Service
@Log
@Profile("!test")
public class EmailSendingService implements EmailSending {
    private final ApplicationConfigurationProvider applicationConfigurationProvider;
    private final JavaMailSender javaMailSender;
    private Map<String, String> contentMap = new HashMap<>();
    private Map<String, String> emailTitles;

    public EmailSendingService(
            @Autowired ApplicationConfigurationProvider applicationConfigurationProvider,
            @Autowired JavaMailSender javaMailSender) {
        this.applicationConfigurationProvider = applicationConfigurationProvider;
        this.javaMailSender = javaMailSender;
        loadEmailTitles();
    }

    private String getEmailTitle(String templateName) {
        String title = emailTitles.get(templateName);
        if (title == null) {
            return "Information about Eunice Platform";
        } else {
            return title;
        }
    }

    @Value("${app.mail.sender}")
    private String sender;

    @Async
    @Override
    public void sendEmail(String receiver, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(receiver);
        message.setSubject(subject);
        message.setText(body);
        this.javaMailSender.send(message);
    }

    @Override
    public String editContent(String content) {
        if (content == null || contentMap == null) {
            throw new IllegalArgumentException("Content and editMap cannot be null");
        }

        for (Map.Entry<String, String> entry : contentMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            content = content.replace(key, value);
        }

        return content;
    }

    private String loadHtmlTemplate(String templateName) throws IOException {

        Path templatePath =
                applicationConfigurationProvider
                        .getEmailTemplatesDirectory()
                        .resolve(templateName + ".html");
        byte[] bytes = Files.readAllBytes(templatePath);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return content;
    }

    private void loadEmailTitles() {
        try {
            Path jsonTitlesPath =
                    applicationConfigurationProvider.getEmailTemplatesDirectory().resolve("emailTitles.json");
            byte[] bytes = Files.readAllBytes(jsonTitlesPath);
            String jsonContent = new String(bytes, StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            JavaType type =
                    TypeFactory.defaultInstance().constructMapType(Map.class, String.class, String.class);
            emailTitles = objectMapper.readValue(jsonContent, type);
        } catch (IOException e) {
            throw new RuntimeException("Error loading email titles from JSON file", e);
        }
    }

    @Async
    @Override
    public void sendConfirmNewAccountEmail(User receiver, String password) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(receiver.getEmail());
            helper.setSubject(getEmailTitle("NEW_USER_ACCOUNT"));
            String emailTemplateContent = loadHtmlTemplate(EmailTemplate.NEW_USER_ACCOUNT.templateName);
            contentMap.put("[user_name]", receiver.getUsername());
            contentMap.put("[user_password]", password);
            contentMap.put("[login_link]", getUrl("auth", "login"));
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + receiver.getEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendEditUserAccountMail(
            String oldEmail, User receiver, String adminUsername, String adminEmail) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(oldEmail);
            helper.setSubject(getEmailTitle("EDIT_USER_ACCOUNT"));
            String emailTemplateContent = loadHtmlTemplate(EmailTemplate.EDIT_USER_ACCOUNT.templateName);
            contentMap.put("[new_username]", receiver.getUsername());
            contentMap.put("[new_mail]", receiver.getEmail());
            contentMap.put("[new_firstname]", receiver.getFirstName());
            contentMap.put("[new_lastname]", receiver.getLastName());
            contentMap.put("[admin_name]", adminUsername);
            contentMap.put("[admin_mail]", adminEmail);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + oldEmail, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendEditUserAccountMail(
            String oldEmail, User receiver, String adminUsername, String adminEmail, String password) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(oldEmail);
            helper.setSubject(getEmailTitle("EDIT_USER_ACCOUNT_WITH_PASSWORD"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.EDIT_USER_ACCOUNT_WITH_PASSWORD.templateName);
            contentMap.put("[new_username]", receiver.getUsername());
            contentMap.put("[new_mail]", receiver.getEmail());
            contentMap.put("[new_firstname]", receiver.getFirstName());
            contentMap.put("[new_lastname]", receiver.getLastName());
            contentMap.put("[new_password]", password);
            contentMap.put("[admin_name]", adminUsername);
            contentMap.put("[admin_mail]", adminEmail);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + oldEmail, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendChangeTicketStatusEmail(Ticket ticket) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(ticket.getRequesterEmail());
            helper.setSubject(getEmailTitle("CHANGE_TICKET_STATUS"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.CHANGE_TICKET_STATUS.templateName);
            contentMap.put("[new_status]", ticket.getStatus().name());
            if (ticket.getRequesterToken() != null) {
                contentMap.put(
                        "[ticket_link]",
                        getUrl("tickets", ticket.getId().toString()) + "?token=" + ticket.getRequesterToken());
            }
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + ticket.getRequesterEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendChangeTicketStatusForCHREmail(Ticket ticket, String author) {
        for (TicketUserStatus ticketUser : ticket.getTicketHandlers()) {
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(sender);
                helper.setTo(ticketUser.getUser().getEmail());
                helper.setSubject(getEmailTitle("CHANGE_TICKET_STATUS_CRH"));
                String emailTemplateContent =
                        loadHtmlTemplate(EmailTemplate.CHANGE_TICKET_STATUS_CRH.templateName);
                contentMap.put("[new_status]", ticket.getStatus().name());
                contentMap.put("[author_changes]", author);
                if (ticket.getRequesterToken() != null) {
                    contentMap.put("[ticket_link]", getUrl("tickets", ticket.getId().toString()));
                }
                emailTemplateContent = editContent(emailTemplateContent);
                helper.setText(emailTemplateContent, true);
                javaMailSender.send(message);
                contentMap.clear();
            } catch (MessagingException e) {
                log.log(Level.SEVERE, "Error sending email to: " + ticketUser.getUser().getEmail(), e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Async
    @Override
    public void sendDeleteAccountEmail(
            User receiver, String administratorUsername, String administratorEmail) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(receiver.getEmail());
            helper.setSubject(getEmailTitle("DELETE_USER_ACCOUNT"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.DELETE_USER_ACCOUNT.templateName);
            contentMap.put("[admin_name]", administratorUsername);
            contentMap.put("[admin_mail]", administratorEmail);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + receiver.getEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendDisableAccountEmail(
            User receiver, String administratorUsername, String administratorEmail) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(receiver.getEmail());
            helper.setSubject(getEmailTitle("DISABLE_USER_ACCOUNT"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.DISABLE_USER_ACCOUNT.templateName);
            contentMap.put("[admin_name]", administratorUsername);
            contentMap.put("[admin_mail]", administratorEmail);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            contentMap.clear();
            javaMailSender.send(message);
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + receiver.getEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendEnableAccountEmail(
            User receiver, String administratorUsername, String administratorEmail) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(receiver.getEmail());
            helper.setSubject(getEmailTitle("ENABLE_USER_ACCOUNT"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.ENABLE_USER_ACCOUNT.templateName);
            contentMap.put("[admin_name]", administratorUsername);
            contentMap.put("[admin_mail]", administratorEmail);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            contentMap.clear();
            javaMailSender.send(message);
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + receiver.getEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendNewResponseInTicketEmail(Ticket ticket, String author, String content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(ticket.getRequesterEmail());
            helper.setSubject(getEmailTitle("NEW_RESPONSE_TICKET"));
            String emailTemplateContent =
                    loadHtmlTemplate(EmailTemplate.NEW_RESPONSE_TICKET.templateName);
            contentMap.put(
                    "[ticket_link]",
                    getUrl("tickets", ticket.getId().toString()) + "?token=" + ticket.getRequesterToken());
            contentMap.put("[content_response]", content);
            contentMap.put("[author_response]", author);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + ticket.getRequesterEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendNewResponseInTicketEmail(
            Ticket ticket, String author, String content, Set<TicketUserStatus> ticketUserStatus) {
        for (TicketUserStatus ticketUser : ticketUserStatus) {
            String receiverEmail = ticketUser.getUser().getEmail();
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(sender);
                helper.setTo(receiverEmail);
                helper.setSubject(getEmailTitle("NEW_RESPONSE_TICKET"));
                String emailTemplateContent =
                        loadHtmlTemplate(EmailTemplate.NEW_RESPONSE_TICKET.templateName);
                contentMap.put("[ticket_link]", getUrl("tickets", ticket.getId().toString()));
                contentMap.put("[content_response]", content);
                contentMap.put("[author_response]", author);
                emailTemplateContent = editContent(emailTemplateContent);
                helper.setText(emailTemplateContent, true);
                javaMailSender.send(message);
                contentMap.clear();
            } catch (MessagingException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Async
    @Override
    public void sendNewRequestEmail(Ticket ticket, String author, String content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(ticket.getRequesterEmail());
            helper.setSubject(getEmailTitle("NEW_TICKET"));
            String emailTemplateContent = loadHtmlTemplate(EmailTemplate.NEW_TICKET.templateName);
            contentMap.put(
                    "[ticket_link]",
                    getUrl("tickets", ticket.getId().toString()) + "?token=" + ticket.getRequesterToken());
            contentMap.put("[request_content]", content);
            contentMap.put("[request_author]", author);
            emailTemplateContent = editContent(emailTemplateContent);
            helper.setText(emailTemplateContent, true);
            javaMailSender.send(message);
            contentMap.clear();
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Error sending email to: " + ticket.getRequesterEmail(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Override
    public void sendNewRequestEmailCRH(
            Ticket ticket, String author, String content, Set<TicketUserStatus> ticketUserStatus) {
        for (TicketUserStatus ticketUser : ticketUserStatus) {
            String receiverEmail = ticketUser.getUser().getEmail();
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(sender);
                helper.setTo(receiverEmail);
                helper.setSubject(getEmailTitle("NEW_TICKET"));
                String emailTemplateContent = loadHtmlTemplate(EmailTemplate.NEW_TICKET.templateName);
                contentMap.put("[ticket_link]", getUrl("tickets", ticket.getId().toString()));
                contentMap.put("[request_content]", content);
                contentMap.put("[request_author]", author);
                emailTemplateContent = editContent(emailTemplateContent);
                helper.setText(emailTemplateContent, true);
                javaMailSender.send(message);
                contentMap.clear();
            } catch (MessagingException e) {
                log.log(Level.SEVERE, "Error sending email to: " + receiverEmail, e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getUrl(String... pathSegments) {
        try {
            return UriComponentsBuilder.fromHttpUrl(
                            this.applicationConfigurationProvider.getApplicationServer())
                    .pathSegment(pathSegments)
                    .build()
                    .normalize()
                    .toUri()
                    .toURL()
                    .toString();
        } catch (MalformedURLException e) {
            return (this.applicationConfigurationProvider.getApplicationServer()
                            + "/"
                            + String.join("/", pathSegments))
                    .replaceAll("//", "/");
        }
    }
}
