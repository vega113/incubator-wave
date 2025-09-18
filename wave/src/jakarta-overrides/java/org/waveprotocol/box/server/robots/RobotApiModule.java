/** Jakarta variant of RobotApiModule wiring Jakarta robot servlets. */
package org.waveprotocol.box.server.robots;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterModule;
import com.google.wave.api.robot.HttpRobotConnection;
import com.google.wave.api.robot.RobotConnection;
import com.typesafe.config.Config;
import net.oauth.OAuthServiceProvider;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.waveprotocol.box.server.robots.active.ActiveApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiOperationServiceRegistry;
import org.waveprotocol.box.server.robots.passive.RobotConnector;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class RobotApiModule extends AbstractModule {
  private static final int NUMBER_OF_THREADS = 10;
  private static final String AUTHORIZE_TOKEN_PATH = "/OAuthAuthorizeToken";
  private static final String REQUEST_TOKEN_PATH = "/OAuthGetRequestToken";
  private static final String ACCESS_TOKEN_PATH = "/OAuthGetAccessToken";
  private static final String ALL_TOKENS_PATH = "/OAuthGetAllTokens";

  @Override
  protected void configure() {
    install(new EventDataConverterModule());
    install(new org.waveprotocol.box.server.robots.RobotSerializerModule());

    bind(String.class).annotatedWith(Names.named("authorize_token_path"))
        .toInstance(AUTHORIZE_TOKEN_PATH);
    bind(String.class).annotatedWith(Names.named("request_token_path"))
        .toInstance(REQUEST_TOKEN_PATH);
    bind(String.class).annotatedWith(Names.named("access_token_path"))
        .toInstance(ACCESS_TOKEN_PATH);
    bind(String.class).annotatedWith(Names.named("all_tokens_path"))
        .toInstance(ALL_TOKENS_PATH);
  }

  @Provides
  @Singleton
  @Inject
  protected RobotConnector provideRobotConnector(RobotConnection connection,
                                                 RobotSerializer serializer) {
    return new RobotConnector(connection, serializer);
  }

  @Provides
  @Singleton
  protected RobotConnection provideRobotConnection() {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(50);
    cm.setDefaultMaxPerRoute(10);
    CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("RobotConnection").build();
    return new HttpRobotConnection(httpClient,
        Executors.newFixedThreadPool(NUMBER_OF_THREADS, threadFactory));
  }

  @Provides
  @Singleton
  @Named("GatewayExecutor")
  protected Executor provideGatewayExecutor() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("PassiveRobotRunner").build();
    return Executors.newFixedThreadPool(NUMBER_OF_THREADS, threadFactory);
  }

  @Provides
  @Singleton
  @Inject
  @Named("ActiveApiRegistry")
  protected org.waveprotocol.box.server.robots.OperationServiceRegistry provideActiveApiRegistry(
      Injector injector) {
    return new ActiveApiOperationServiceRegistry(injector);
  }

  @Provides
  @Singleton
  @Inject
  @Named("DataApiRegistry")
  protected org.waveprotocol.box.server.robots.OperationServiceRegistry provideDataApiRegistry(
      Injector injector) {
    return new DataApiOperationServiceRegistry(injector);
  }

  @Provides
  @Singleton
  protected OAuthValidator provideOAuthValidator() {
    return new SimpleOAuthValidator();
  }

  @Provides
  @Singleton
  protected OAuthServiceProvider provideOAuthServiceProvider(Config config) {
    String publicAddress = config.getString("core.http_frontend_public_address");
    String requestTokenUrl = getOAuthUrl(publicAddress, REQUEST_TOKEN_PATH);
    String authorizeTokenUrl = getOAuthUrl(publicAddress, AUTHORIZE_TOKEN_PATH);
    String accessTokenUrl = getOAuthUrl(publicAddress, ACCESS_TOKEN_PATH);
    return new OAuthServiceProvider(requestTokenUrl, authorizeTokenUrl, accessTokenUrl);
  }

  private String getOAuthUrl(String publicAddress, String postFix) {
    return String.format("http://%s%s%s", publicAddress, DataApiOAuthServlet.DATA_API_OAUTH_PATH, postFix);
  }
}
