package be.kuleuven.distributedsystems.cloud;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.HypermediaWebClientConfigurer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.Objects;

@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@SpringBootApplication
public class Application {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException {
        System.setProperty("server.port", System.getenv().getOrDefault("PORT", "8080"));

        ApplicationContext context = SpringApplication.run(Application.class, args);


        if (!Objects.equals(System.getenv("GAE_ENV"), "standard"))
        {
            String projectId = "demo-distributed-systems-kul";
            String subscriptionId = "subscription";
            String topicId = "booking";
            String pushEndpoint = "http://localhost:8080/booking";

            TopicName topicName = TopicName.of(projectId, topicId);
            String hostport = "localhost:8083";

            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostport).usePlaintext().build();
            try {


                TransportChannelProvider channelProvider =
                        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
                CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

                TopicAdminClient topicClient =
                        TopicAdminClient.create(
                                TopicAdminSettings.newBuilder()
                                        .setTransportChannelProvider(channelProvider)
                                        .setCredentialsProvider(credentialsProvider)
                                        .build());

                //create subscription
                SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(
                        SubscriptionAdminSettings.newBuilder()
                                .setEndpoint(pushEndpoint)
                                .setCredentialsProvider(credentialsProvider)
                                .setTransportChannelProvider(channelProvider)
                                .build()

                );
                SubscriptionName subscriptionName = SubscriptionName.of(projectId, subscriptionId);
                PushConfig pushConfig = PushConfig.newBuilder()
                        .setPushEndpoint(pushEndpoint)
                        .build();

                // Create a push subscription with default acknowledgement deadline of 10 seconds.
                // Messages not successfully acknowledged within 10 seconds will get resent by the server.

                try {
                    var response = topicClient.createTopic(topicName);
                    System.out.printf("Topic %s created.\n", response);

                    var subscription =
                            subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 10);

                    System.out.println("Created push subscription: " + subscription.getName());
                } catch (ApiException e) {
                    System.out.println(e.getStatusCode().getCode());
                    System.out.println(e.isRetryable());
                    System.out.println("No topic was created.");
                }

            } finally {
                channel.shutdown();
            }
        }

        // TODO: (level 2) load this data into Firestore
        //String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());
        //OwnDatabaseStartupBean.initDb(data);
    }

    @Bean
    public boolean isProduction() {
        return Objects.equals(System.getenv("GAE_ENV"), "standard");
    }

    @Bean
    public String projectId() {
        if(isProduction())
            return "distsys-flight-app";

        return "demo-distributed-systems-kul";
    }

    /*
     * You can use this builder to create a Spring WebClient instance which can be used to make REST-calls.
     */
    @Bean
    WebClient.Builder webClientBuilder(HypermediaWebClientConfigurer configurer) {
        return configurer.registerHypermediaTypes(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)));
    }

    @Bean
    HttpFirewall httpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
}
