package put.eunice.cms.resource;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import put.eunice.cms.resource.storage.StorageService;

@Service
@RequiredArgsConstructor
public class FileService {
    private final StorageService storageService;

    public String store(MultipartFile file, String filename) throws IOException {
        return storageService.store(file, filename);
    }

    public String store(MultipartFile file, String filename, String directory) throws IOException {
        return storageService.store(file, filename, directory);
    }

    public void deleteDirectory(String directory) throws IOException {
        storageService.deleteDirectory(directory);
    }

    public void renameDirectory(String sourceDirectory, String targetDirectory) throws IOException {
        storageService.renameDirectory(sourceDirectory, targetDirectory);
    }
}
