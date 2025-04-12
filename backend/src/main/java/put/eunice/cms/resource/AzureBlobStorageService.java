package put.eunice.cms.resource;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.ListBlobsOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AzureBlobStorageService {
    private final BlobServiceClient blobServiceClient;
    private final BlobContainerClient blobContainerClient;
    private final String containerName;

    public AzureBlobStorageService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name}") String containerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
        this.blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    public String store(MultipartFile file, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        var blobClient = blobContainerClient.getBlobClient(filename);

        try (var inputStream = file.getInputStream()) {
            blobClient.upload(inputStream, file.getSize(), true);
        }

        return blobClient.getBlobUrl();
    }

    public String store(MultipartFile file, String filename, String directory) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        return this.store(file, directory + "/" + filename);
    }

    public byte[] getContent(String blobName) {
        var outputStream = new ByteArrayOutputStream();
        blobContainerClient.getBlobClient(blobName).downloadStream(outputStream);

        return outputStream.toByteArray();
    }

    public void deleteBlob(String blobName) {
        blobContainerClient.getBlobClient(blobName).delete();
    }

    public void deleteDirectory(String directory) {
        blobContainerClient
                .listBlobs(new ListBlobsOptions().setPrefix(directory + "/"), Duration.ofSeconds(30))
                .forEach(blobItem -> blobContainerClient.getBlobClient(blobItem.getName()).delete());
    }

    public void renameDirectory(String sourceDirectory, String targetDirectory) {
        blobContainerClient
                .listBlobs(new ListBlobsOptions().setPrefix(sourceDirectory + "/"), Duration.ofSeconds(30))
                .forEach(
                        blobItem -> {
                            String sourceBlobName = blobItem.getName();
                            String targetBlobName = sourceBlobName.replace(sourceDirectory, targetDirectory);

                            // Copy the blob to the new location
                            BlobClient sourceBlob = blobContainerClient.getBlobClient(sourceBlobName);
                            BlobClient targetBlob = blobContainerClient.getBlobClient(targetBlobName);
                            targetBlob.beginCopy(sourceBlob.getBlobUrl(), null);

                            // Delete the source blob
                            sourceBlob.delete();
                        });
    }
}
