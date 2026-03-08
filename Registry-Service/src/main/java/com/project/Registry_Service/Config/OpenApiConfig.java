package com.project.Registry_Service.Config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * Access at: /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${server.port:8081}")
    private int serverPort;

    @Bean
    public OpenAPI registryServiceOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Local development server");

        Contact contact = new Contact();
        contact.setName("Registry Service Team");
        contact.setEmail("registry@example.com");

        License license = new License()
            .name("Apache 2.0")
            .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
            .title("Sentinel Registry Service API")
            .version("1.0.0")
            .description("Self-healing microservice registry with failure detection and automatic recovery")
            .contact(contact)
            .license(license);

        return new OpenAPI()
            .info(info)
            .servers(List.of(localServer));
    }
}
