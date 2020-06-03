package br.com.greenpay.files.upload.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.servlet.ServletException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Acl.Role;
import com.google.cloud.storage.Acl.User;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Service
public class FilesStorageServiceImpl implements FilesStorageService {

	private final Path root = Paths.get("uploads");

	@Override
	public void init() {
		try {
			Files.createDirectory(root);
		} catch (IOException e) {
			throw new RuntimeException("Could not initialize folder for upload!");
		}
	}

	@Override
	public void save(MultipartFile file, Long productId) {

		try {
			firestore(file, productId);
		} catch (Exception e) {
			throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
		}

//		try {
//			Files.copy(file.getInputStream(), this.root.resolve(file.getOriginalFilename()));
//		} catch (Exception e) {
//			throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
//		}

	}

	@Override
	public Resource load(String filename) {
		try {
			Path file = root.resolve(filename);
			Resource resource = new UrlResource(file.toUri());

			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new RuntimeException("Could not read the file!");
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error: " + e.getMessage());
		}
	}

	@Override
	public void deleteAll() {
		FileSystemUtils.deleteRecursively(root.toFile());
	}

	@Override
	public Stream<Path> loadAll() {
		try {
			return Files.walk(this.root, 1).filter(path -> !path.equals(this.root)).map(this.root::relativize);
		} catch (IOException e) {
			throw new RuntimeException("Could not load the files!");
		}
	}

	private void firestore(MultipartFile file, Long productId) throws IOException {
		
		InputStream is = null;
		try {
			is = getClass().getResourceAsStream("/green-pay-v1-63f74ec603a0.json");
		} catch (Exception e) {
			e.printStackTrace();
		}
		

		StorageOptions storageOptions = StorageOptions.newBuilder().setProjectId("green-pay-v1 ")
				.setCredentials(GoogleCredentials
						.fromStream(is))
				.build();
		Storage storage = storageOptions.getService();

		try {
			checkFileExtension(file.getName());
		} catch (ServletException e) {
			e.printStackTrace();
		}
		DateTimeFormatter dtf = DateTimeFormat.forPattern("-YYYY-MM-dd-HHmmssSSS");
		DateTime dt = DateTime.now(DateTimeZone.UTC);
		String dtString = dt.toString(dtf);
		final String fileName = file.getName() + dtString;

		@SuppressWarnings("deprecation")
		BlobInfo blobInfo = storage.create(
				BlobInfo.newBuilder("green-pay-v1.appspot.com", fileName).setContentType("image/png")
						// Modify access list to allow all users with link to read file
						.setAcl(new ArrayList<>(Arrays.asList(Acl.of(User.ofAllUsers(), Role.READER)))).build(),
				file.getInputStream());

		System.out.println(blobInfo.getMediaLink() + " / " + productId);
		callProduct(productId, blobInfo.getMediaLink());
	}

	private void checkFileExtension(String fileName) throws ServletException {
		if (fileName != null && !fileName.isEmpty() && fileName.contains(".")) {
			String[] allowedExt = { ".jpg", ".jpeg", ".png", ".gif" };
			for (String ext : allowedExt) {
				if (fileName.endsWith(ext)) {
					return;
				}
			}
			throw new ServletException("file must be an image");
		}
	}

	private String callProduct(Long productId, String urlPhoto) {
//		final String uri = "http://localhost:8080/products/image";
		final String uri = "https://green-pay-v1.uc.r.appspot.com//products/image";
		UpdatePhotoResponse updatePhotoResponse = new UpdatePhotoResponse();
		updatePhotoResponse.setProductId(productId);
		updatePhotoResponse.setUrlPhoto(urlPhoto);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity<UpdatePhotoResponse> entity = new HttpEntity<UpdatePhotoResponse>(updatePhotoResponse, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> result = restTemplate.postForEntity(uri, entity, String.class);
		return result.getBody();
	}

}
