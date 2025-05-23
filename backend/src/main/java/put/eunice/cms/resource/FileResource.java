package put.eunice.cms.resource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.Length;
import put.eunice.cms.page.Page;
import put.eunice.cms.user.User;

@Entity
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "resources")
public class FileResource {
    public static final String STORE_DIRECTORY = "files/";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Length(max = 255, message = "ERRORS.RESOURCE.400.NAME_TOO_LONG")
    @NotBlank(message = "ERRORS.RESOURCE.400.NAME_EMPTY")
    private String name;

    @Length(max = 255, message = "ERRORS.RESOURCE.400.DESCRIPTION_TOO_LONG")
    @NotBlank(message = "ERRORS.RESOURCE.400.DESCRIPTION_EMPTY")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String path;

    private String fileType;

    @NotNull(message = "ERRORS.RESOURCE.400.SIZE_EMPTY")
    private Long size = 0L;

    private ResourceType resourceType = ResourceType.FILE;

    private Timestamp createdOn;
    private Timestamp updatedOn;

    @ManyToOne(fetch = FetchType.EAGER)
    private User author;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "page_resources",
            joinColumns = @JoinColumn(name = "resource_id"),
            inverseJoinColumns = @JoinColumn(name = "page_id"))
    private Set<Page> pages = new HashSet<>();

    @PrePersist
    protected void prePersist() {
        var now = Timestamp.valueOf(LocalDateTime.now());
        createdOn = now;
        updatedOn = now;
    }

    @PreUpdate
    protected void preMerge() {
        updatedOn = Timestamp.valueOf(LocalDateTime.now());
    }

    public FileResource(String name, String description, User author) {
        this.name = name;
        this.description = description;
        this.author = author;
    }

    public void setAsFileResource(String path, String fileType, Long size) {
        this.path = path;
        this.fileType = fileType;
        this.size = size;
        if (path.startsWith("http")) {
            this.resourceType = ResourceType.FILE_EXTERNAL_STORAGE;
        } else {
            this.resourceType =
                    fileType.split("/")[0].equals("image") ? ResourceType.IMAGE : ResourceType.FILE;
        }
    }

    public void setAsLinkResource(String url) {
        this.path = url;
        this.resourceType = ResourceType.LINK;
    }

    public String getBrowserSafeUrl() {
        if (this.resourceType.equals(ResourceType.FILE)) {
            return "/api/resources/" + this.id + "/download";
        }

        if (this.resourceType.equals(ResourceType.LINK)) {
            return this.getPath();
        }

        if (this.resourceType.equals(ResourceType.FILE_EXTERNAL_STORAGE)) {
            return this.getPath();
        }

        return this.getPath().replaceAll(".*" + STORE_DIRECTORY, "/static/" + STORE_DIRECTORY);
    }
}
