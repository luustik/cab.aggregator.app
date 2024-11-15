package cab.aggregator.app.ratingservice.integration;

import cab.aggregator.app.ratingservice.kafka.KafkaProducerConfig;
import cab.aggregator.app.ratingservice.kafka.KafkaSender;
import cab.aggregator.app.ratingservice.kafka.KafkaTopic;
import cab.aggregator.app.ratingservice.repository.RatingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static cab.aggregator.app.ratingservice.utils.RatingConstants.ALTER_RATING_SEQ;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.AVG_RATING_USER_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVERS_ID_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVER_RATING;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVER_RESOURCE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVER_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVER_ROLE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.DRIVER_WIRE_MOCK_SERVER_PORT;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.NEW_RATING_DRIVER;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.NOT_VALID_ROLE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.PASSENGERS_ID_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.PASSENGER_RESOURCE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.PASSENGER_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.PASSENGER_ROLE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.PASSENGER_WIRE_MOCK_SERVER_PORT;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.POSTGRESQL_CONTAINER;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_AVG_RATING_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_ID_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_RIDE_ROLE_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_ROLE_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATINGS_USER_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER_CONTAINER_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER_NOT_EXISTS_RIDE_REQUEST;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER_NOT_EXISTS_USER_REQUEST;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER_REQUEST;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_DRIVER_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_NOT_EXISTS_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_RESOURCE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RATING_UPDATE_DTO;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDES_ID_URL;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDE_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDE_ID_EXIST;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDE_RESOURCE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDE_RESPONSE;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.RIDE_WIRE_MOCK_SERVER_PORT;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.USER_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.USER_NOT_EXISTS_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.VALID_NOT_EXISTS_ID;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.getEmtpyListMessageMap;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.getNotFoundByIdMessageMap;
import static cab.aggregator.app.ratingservice.utils.RatingConstants.getNotFoundMessageMap;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockBean({KafkaSender.class, KafkaProducerConfig.class, KafkaTopic.class})
public class RatingControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(POSTGRESQL_CONTAINER);

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final WireMockServer rideWireMockServer = new WireMockServer(RIDE_WIRE_MOCK_SERVER_PORT);
    private static final WireMockServer passengerWireMockServer = new WireMockServer(PASSENGER_WIRE_MOCK_SERVER_PORT);
    private static final WireMockServer driverWireMockServer = new WireMockServer(DRIVER_WIRE_MOCK_SERVER_PORT);

    @BeforeEach
    void setUp() {
        RestAssured.port = this.port;
        ratingRepository.deleteAll();
        jdbcTemplate.execute(ALTER_RATING_SEQ);
        ratingRepository.save(RATING_DRIVER);
        rideWireMockServer.start();
        passengerWireMockServer.start();
        driverWireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        rideWireMockServer.stop();
        passengerWireMockServer.stop();
        driverWireMockServer.stop();
    }

    @Test
    void getRatingById_whenRatingExist_returnRatingResponseAndStatusOk() throws Exception {
        given()
                .when()
                .get(RATINGS_ID_URL, RATING_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_RESPONSE)));
    }

    @Test
    void getRatingById_whenRatingNotExist_returnStatusNotFound() throws Exception {
        given()
                .when()
                .get(RATINGS_ID_URL, RATING_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(RATING_RESOURCE, RATING_NOT_EXISTS_ID))));
    }

    @Test
    void getRatingByRideIdAndRole_whenRatingExist_returnRatingResponseAndStatusOk() throws Exception {
        stubForRideService_whenRideExists();
        given()
                .when()
                .get(RATINGS_RIDE_ROLE_URL, DRIVER_ROLE, RIDE_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_RESPONSE)));
    }

    @Test
    void getRatingByRideIdAndRole_whenRoleNotValid_returnBadRequest() {
        given()
                .when()
                .get(RATINGS_RIDE_ROLE_URL, NOT_VALID_ROLE, RIDE_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void getRatingByRideIdAndRole_whenRatingNotExist_returnStatusNotFound() throws Exception {
        ratingRepository.deleteAll();
        stubForRideService_whenRideExists();
        given()
                .when()
                .get(RATINGS_RIDE_ROLE_URL, DRIVER_ROLE, RIDE_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundMessageMap(DRIVER_RATING, RIDE_ID_EXIST))));
    }

    @Test
    void getRatingByRideIdAndRole_whenRideNotExist_returnStatusNotFound() throws Exception {
        stubForRideService_whenRideNotExists();
        given()
                .when()
                .get(RATINGS_RIDE_ROLE_URL, DRIVER_ROLE, VALID_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(RIDE_RESOURCE, VALID_NOT_EXISTS_ID))));
    }

    @Test
    void getAllRatings_returnRatingContainerResponseAndStatusOk() throws Exception {
        given()
                .when()
                .get(RATINGS_URL)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_CONTAINER_RESPONSE)));
    }

    @Test
    void getAllRatingsByRole_returnRatingContainerResponseAndStatusOk() throws Exception {
        given()
                .when()
                .get(RATINGS_ROLE_URL, DRIVER_ROLE)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_CONTAINER_RESPONSE)));
    }

    @Test
    void getAllRatingsByRole_whenRoleNotValid_returnBadRequest() {
        given()
                .when()
                .get(RATINGS_ROLE_URL, NOT_VALID_ROLE)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void getAllRatingsByUserIdAndRole_returnRatingContainerResponseAndStatusOk() throws Exception {
        stubForUserWhenUserExists(USER_ID, DRIVER_ROLE);
        given()
                .when()
                .get(RATINGS_USER_URL, DRIVER_ROLE, USER_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_CONTAINER_RESPONSE)));
    }

    @Test
    void getAllRatingsByUserIdAndRole_whenDriverNotExists_returnStatusNotFound() throws Exception {
        stubForUserWhenUserNotExists(USER_NOT_EXISTS_ID, DRIVER_ROLE);
        given()
                .when()
                .get(RATINGS_USER_URL, DRIVER_ROLE, USER_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundMessageMap(DRIVER_RESOURCE, USER_NOT_EXISTS_ID))));
    }

    @Test
    void getAllRatingsByUserIdAndRole_whenPassengerNotExists_returnStatusNotFound() throws Exception {
        stubForUserWhenUserNotExists(USER_NOT_EXISTS_ID, PASSENGER_ROLE);
        given()
                .when()
                .get(RATINGS_USER_URL, PASSENGER_ROLE, USER_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(PASSENGER_RESOURCE, USER_NOT_EXISTS_ID))));
    }

    @Test
    void getAllRatingsByUserIdAndRole_whenRoleNotValid_returnBadRequest() {
        given()
                .when()
                .get(RATINGS_ROLE_URL, NOT_VALID_ROLE)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void deleteRatingById_whenRatingExistButListRatingsUserIsEmpty_returnBadRequest() throws Exception {
        given()
                .when()
                .delete(RATINGS_ID_URL, RATING_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getEmtpyListMessageMap())));
    }

    @Test
    void deleteRatingById_whenRatingExistAndListRatingsUserNotEmpty_returnStatusNoContent() {
        ratingRepository.save(NEW_RATING_DRIVER);
        given()
                .when()
                .delete(RATINGS_ID_URL, RATING_ID)
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void deleteRatingById_whenRatingNotExist_returnStatusNotfound() throws Exception {
        given()
                .when()
                .delete(RATINGS_ID_URL, RATING_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(RATING_RESOURCE, RATING_NOT_EXISTS_ID))));
    }

    @Test
    void createRating_whenUserNotExists_returnStatusNotFound() throws Exception {
        stubForUserWhenUserNotExists(VALID_NOT_EXISTS_ID, DRIVER_ROLE);
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RATING_DRIVER_NOT_EXISTS_USER_REQUEST)
                .when()
                .post(RATINGS_URL)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundMessageMap(DRIVER_RESOURCE, VALID_NOT_EXISTS_ID))));
    }

    @Test
    void createRating_whenRideNotExists_returnStatusNotFound() throws Exception {
        stubForUserWhenUserExists(USER_ID, DRIVER_ROLE);
        stubForRideService_whenRideNotExists();
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RATING_DRIVER_NOT_EXISTS_RIDE_REQUEST)
                .when()
                .post(RATINGS_URL)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(RIDE_RESOURCE, VALID_NOT_EXISTS_ID))));
    }

    @Test
    void createRating_returnStatusCreatedAndRatingResponse() throws Exception {
        ratingRepository.deleteAll();
        jdbcTemplate.execute(ALTER_RATING_SEQ);
        stubForUserWhenUserExists(USER_ID, DRIVER_ROLE);
        stubForRideService_whenRideExists();
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RATING_DRIVER_REQUEST)
                .when()
                .post(RATINGS_URL)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_RESPONSE)));
    }

    @Test
    void updateRating_whenRatingExist_returnRatingResponseAndStatusOk() throws Exception {
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RATING_UPDATE_DTO)
                .when()
                .patch(RATINGS_ID_URL, RATING_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(RATING_DRIVER_RESPONSE)));
    }

    @Test
    void updateRating_whenRatingNotExist_returnStatusNotFound() throws Exception {
        ratingRepository.deleteAll();
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RATING_UPDATE_DTO)
                .when()
                .patch(RATINGS_ID_URL, RATING_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundByIdMessageMap(RATING_RESOURCE, RATING_ID))));
    }

    @Test
    void calculateAvgRatingUser_whenUserExists_returnAvgRatingResponseAndStatusOk() throws Exception {
        stubForUserWhenUserExists(USER_ID, DRIVER_ROLE);
        given()
                .when()
                .get(RATINGS_AVG_RATING_URL, DRIVER_ROLE, USER_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(AVG_RATING_USER_RESPONSE)));
    }

    @Test
    void calculateAvgRatingUser_whenUserExistsButListRatingEmpty_returnBadRequest() throws Exception {
        ratingRepository.deleteAll();
        stubForUserWhenUserExists(USER_ID, DRIVER_ROLE);
        given()
                .when()
                .get(RATINGS_AVG_RATING_URL, DRIVER_ROLE, USER_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getEmtpyListMessageMap())));
    }

    @Test
    void calculateAvgRatingUser_whenUserNotExists_returnNotFound() throws Exception {
        stubForUserWhenUserNotExists(VALID_NOT_EXISTS_ID, DRIVER_ROLE);
        given()
                .when()
                .get(RATINGS_AVG_RATING_URL, DRIVER_ROLE, VALID_NOT_EXISTS_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(equalTo(objectMapper.writeValueAsString(getNotFoundMessageMap(DRIVER_RESOURCE, VALID_NOT_EXISTS_ID))));
    }

    //Ride Stubs
    private void stubForRideService_whenRideExists() throws Exception {
        rideWireMockServer.stubFor(WireMock.get(urlPathEqualTo(RIDES_ID_URL + RIDE_ID))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(RIDE_RESPONSE))));
    }

    private void stubForRideService_whenRideNotExists() throws Exception {
        rideWireMockServer.stubFor(WireMock.get(urlPathEqualTo(RIDES_ID_URL + VALID_NOT_EXISTS_ID))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper
                                .writeValueAsString(getNotFoundByIdMessageMap(RIDE_RESOURCE, VALID_NOT_EXISTS_ID)))));
    }

    //User Stubs
    private void stubForUserWhenUserExists(Long userId, String role) throws Exception {

        switch (role) {
            case DRIVER_ROLE -> stubForDriverService_whenDriverExists(userId);
            case PASSENGER_ROLE -> stubForPassengerService_whenPassengerExists(userId);
        }
    }

    private void stubForUserWhenUserNotExists(Long userId, String role) throws Exception {

        switch (role) {
            case DRIVER_ROLE -> stubForDriverService_whenDriverNotExists(userId);
            case PASSENGER_ROLE -> stubForPassengerService_whenPassengerNotExists(userId);
        }
    }

    //Passenger stubs
    private void stubForPassengerService_whenPassengerExists(Long userId) throws Exception {
        passengerWireMockServer.stubFor(WireMock.get(urlPathEqualTo(PASSENGERS_ID_URL + userId))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(PASSENGER_RESPONSE))));
    }

    private void stubForPassengerService_whenPassengerNotExists(Long userId) throws Exception {
        passengerWireMockServer.stubFor(WireMock.get(urlPathEqualTo(PASSENGERS_ID_URL + userId))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper
                                .writeValueAsString(getNotFoundByIdMessageMap(PASSENGER_RESOURCE, userId)))));
    }

    //Driver Stubs
    private void stubForDriverService_whenDriverExists(Long userId) throws Exception {
        driverWireMockServer.stubFor(WireMock.get(urlPathEqualTo(DRIVERS_ID_URL + userId))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(DRIVER_RESPONSE))));
    }

    private void stubForDriverService_whenDriverNotExists(Long userId) throws Exception {
        driverWireMockServer.stubFor(WireMock.get(urlPathEqualTo(DRIVERS_ID_URL + userId))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper
                                .writeValueAsString(getNotFoundMessageMap(DRIVER_RESOURCE, userId)))));
    }
}
