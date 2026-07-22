package com.github.xpenatan.easypublishing;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/** Uploads an assembled Maven repository bundle to the Central Publisher Portal. */
@DisableCachingByDefault(because = "Uploading a deployment is an external side effect")
public abstract class UploadToCentralTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBundleFile();

    @Input
    public abstract Property<String> getReleaseRepositoryUrl();

    @Input
    public abstract Property<String> getDeploymentName();

    @Input
    public abstract Property<Boolean> getAutomaticRelease();

    @Internal
    public abstract Property<String> getUsername();

    @Internal
    public abstract Property<String> getPassword();

    @Internal
    public abstract Property<String> getSigningKey();

    @Internal
    public abstract Property<String> getSigningPassword();

    @Input
    public abstract Property<Boolean> getRequireSigning();

    @TaskAction
    public void upload() throws Exception {
        File bundle = getBundleFile().get().getAsFile();
        if (!bundle.isFile() || !bundle.canRead()) {
            throw new GradleException("Release bundle is missing or unreadable: " + bundle);
        }

        String releaseRepositoryUrl = requireConfigured(
            getReleaseRepositoryUrl().getOrElse(""),
            "easyPublishing.releaseRepositoryUrl"
        );
        String username = requireConfigured(getUsername().getOrElse(""), "easyPublishing.username");
        String password = requireConfigured(getPassword().getOrElse(""), "easyPublishing.password");
        if (getRequireSigning().get()) {
            requireConfigured(getSigningKey().getOrElse(""), "easyPublishing.signingKey");
            requireConfigured(getSigningPassword().getOrElse(""), "easyPublishing.signingPassword");
        }

        String publishingType = getAutomaticRelease().get() ? "AUTOMATIC" : "USER_MANAGED";
        String baseUrl = releaseRepositoryUrl.replaceAll("/+$", "");
        String endpoint = baseUrl + "/api/v1/publisher/upload?name="
            + URLEncoder.encode(getDeploymentName().get(), StandardCharsets.UTF_8)
            + "&publishingType=" + publishingType;

        String boundary = "----easy-publishing-" + UUID.randomUUID();
        String prefix = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"bundle\"; filename=\""
            + bundle.getName().replace("\"", "") + "\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";

        String token = Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofMinutes(10))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString(prefix),
                HttpRequest.BodyPublishers.ofFile(bundle.toPath()),
                HttpRequest.BodyPublishers.ofString(suffix)
            ))
            .build();

        HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GradleException(
                "Central Portal upload failed with HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        getLogger().lifecycle(
            "Central Portal accepted deployment {} (publishing type: {}).",
            response.body().trim(),
            publishingType
        );
    }

    private static String requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new GradleException(propertyName + " must be configured");
        }
        return value;
    }
}
