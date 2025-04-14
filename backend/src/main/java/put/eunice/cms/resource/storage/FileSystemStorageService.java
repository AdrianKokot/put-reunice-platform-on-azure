package put.eunice.cms.resource.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import put.eunice.cms.configuration.ApplicationConfigurationProvider;
import put.eunice.cms.resource.FileResource;
import put.eunice.cms.resource.FileUtils;

@Service
@Log
@Profile("!azure")
@RequiredArgsConstructor
public class FileSystemStorageService implements StorageService {

    private final ApplicationConfigurationProvider config;

    public String store(MultipartFile file, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        var fileDestination = FileUtils.getSecureFilePath(this.config.getUploadsDirectory(), filename);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, fileDestination, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/static/" + fileDestination.getFileName().toString();
    }

    public String store(MultipartFile file, String filename, String directory) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        var fileDestination =
                FileUtils.getSecureFilePath(this.config.getUploadsDirectory().resolve(directory), filename);

        if (!Files.exists(fileDestination.getParent())) {
            Files.createDirectories(fileDestination.getParent());
        }

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, fileDestination, StandardCopyOption.REPLACE_EXISTING);
        }

        return fileDestination.toString();
    }

    public void deleteDirectory(String directory) throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(
                this.config.getUploadsDirectory().resolve(directory).toFile());
    }

    public void renameDirectory(String sourceDirectory, String targetDirectory) throws IOException {
        var sourcePath = this.config.getUploadsDirectory().resolve(sourceDirectory);
        var targetPath = this.config.getUploadsDirectory().resolve(targetDirectory);

        if (Files.exists(sourcePath)) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public UrlResource getUrlResource(FileResource resource) throws IOException {
        return new UrlResource(Paths.get(resource.getPath()).toUri());
    }
}
