package put.eunice.cms.resource.storage;

import java.io.IOException;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;
import put.eunice.cms.resource.FileResource;

public interface StorageService {

    String store(MultipartFile file, String filename) throws IOException;

    String store(MultipartFile file, String filename, String directory) throws IOException;

    void deleteDirectory(String directory) throws IOException;

    void renameDirectory(String sourceDirectory, String targetDirectory) throws IOException;

    UrlResource getUrlResource(FileResource resource) throws IOException;
}
