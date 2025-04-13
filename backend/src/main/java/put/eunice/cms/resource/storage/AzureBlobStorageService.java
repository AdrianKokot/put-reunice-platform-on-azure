package put.eunice.cms.resource.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.ListBlobsOptions;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import put.eunice.cms.resource.FileResource;

@Service
@Log
@Profile("azure")
public class AzureBlobStorageService implements StorageService {
    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name}") String containerName) {
        this.blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    public String store(MultipartFile file, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        log.info("Storing file: " + filename);

        var blobClient = blobContainerClient.getBlobClient(filename);

        try (var inputStream = file.getInputStream()) {
            blobClient.upload(inputStream, file.getSize(), true);
        }

        var url = blobClient.getBlobUrl();
        log.info("File stored successfully: " + filename + " at " + url);

        return url;
    }

    public String store(MultipartFile file, String filename, String directory) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        return this.store(file, directory + "/" + filename);
    }

    public void deleteDirectory(String directory) {

        log.info("Deleting directory: " + directory);
        blobContainerClient
                .listBlobs(new ListBlobsOptions().setPrefix(directory + "/"), Duration.ofSeconds(30))
                .forEach(blobItem -> blobContainerClient.getBlobClient(blobItem.getName()).delete());

        log.info("Directory deleted successfully: " + directory);
    }

    public void renameDirectory(String sourceDirectory, String targetDirectory) {

        log.info("Renaming directory from " + sourceDirectory + " to " + targetDirectory);

        blobContainerClient
                .listBlobs(new ListBlobsOptions().setPrefix(sourceDirectory + "/"), Duration.ofSeconds(30))
                .forEach(
                        blobItem -> {
                            var sourceBlobName = blobItem.getName();
                            var targetBlobName = sourceBlobName.replace(sourceDirectory, targetDirectory);

                            var sourceBlob = blobContainerClient.getBlobClient(sourceBlobName);
                            var targetBlob = blobContainerClient.getBlobClient(targetBlobName);
                            targetBlob.beginCopy(sourceBlob.getBlobUrl(), null);

                            sourceBlob.delete();
                        });

        log.info("Directory renamed successfully from " + sourceDirectory + " to " + targetDirectory);
    }

    public UrlResource getUrlResource(FileResource resource) throws IOException {
        return new UrlResource(resource.getPath());
    }
}
