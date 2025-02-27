package ru.practicum.explorewithme.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.explorewithme.clients.server.priv.PrivateClient;
import ru.practicum.explorewithme.clients.server.PublicClient;
import ru.practicum.explorewithme.clients.server.admin.CategoryClient;
import ru.practicum.explorewithme.clients.server.admin.CompilationsClient;
import ru.practicum.explorewithme.clients.server.admin.EventClient;
import ru.practicum.explorewithme.clients.server.admin.UserClient;
import ru.practicum.explorewithme.clients.server.priv.SubscriptionClient;

@Configuration
public class ClientsConfig {
    @Value("${main-server.url}")
    private String serverUrl;

    private RestTemplateBuilder builder;

    @Autowired
    public ClientsConfig(RestTemplateBuilder builder) {
        this.builder = builder;
    }

    @Bean
    public UserClient makeUserClient() {
        String prefix = "/admin/users";
        return new UserClient(makeRestTemplate(prefix));
    }

    @Bean
    public CategoryClient makeCategoryClient() {
        String prefix = "/admin/categories";
        return new CategoryClient(makeRestTemplate(prefix));
    }

    @Bean
    public CompilationsClient makeCompilationsClient() {
        String prefix = "/admin/compilations";
        return new CompilationsClient(makeRestTemplate(prefix));
    }

    @Bean
    public EventClient makeEventClient() {
        String prefix = "/admin/events";
        return new EventClient(makeRestTemplate(prefix));
    }

    @Bean
    public PrivateClient makePrivateClient() {
        String prefix = "/users";
        return new PrivateClient(makeRestTemplate(prefix));
    }

    @Bean
    public PublicClient makePublicClient() {
        return new PublicClient(makeRestTemplate(""));
    }

    @Bean
    public SubscriptionClient makeSubscriptionClient() {
        String prefix = "/users";
        return new SubscriptionClient(makeRestTemplate(prefix));
    }

    private RestTemplate makeRestTemplate(String prefix) {
        return builder
                .uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl + prefix))  //фабрика для построения URI
                .requestFactory(HttpComponentsClientHttpRequestFactory::new)  //фабрика для создания HTTPRequest
                .build();
    }
}
