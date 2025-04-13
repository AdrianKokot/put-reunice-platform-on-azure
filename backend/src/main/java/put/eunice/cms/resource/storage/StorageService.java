package put.eunice.cms.resource.storage;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String store(MultipartFile file, String filename) throws IOException;

    String store(MultipartFile file, String filename, String directory) throws IOException;

    void deleteDirectory(String directory) throws IOException;

    void renameDirectory(String sourceDirectory, String targetDirectory) throws IOException;
}
