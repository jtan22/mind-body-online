package com.bw.mindbodyonline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;

@SpringBootApplication
public class MindBodyOnlineApplication implements MindBodyOnlineConstants {

	@Value("${username}")
	private String username;
	@Value("${password}")
	private String password;

	private String sessionCode = null;
	private String loginToken = null;

	public static void main(String[] args) {
		SpringApplication.run(MindBodyOnlineApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner() {
		return args -> {
			System.out.println("Application has started. CommandLineRunner is executing");
			if (args.length != 3) {
				printUsage();
				args = getDefaultParameters();
				printParameters(args);
				waitForMidnight();
			} else {
				printParameters(args);
			}
			preLogin();
			doBooking(args[0], args[1], args[2]);
		};
	}

	private void preLogin() {
		if (loginToken != null) {
			return;
		}
		try {
			HttpResponse<String> newSessionResponse = requestNewSession();
			sessionCode = getSessionCode(newSessionResponse.headers().map().get("Set-Cookie"));
			requestLogin(sessionCode, getFormAuthenticityToken(newSessionResponse.body()));
			HttpResponse<String> clientEditResponse = requestClientEdit(sessionCode);
			loginToken = getLoginAuthenticityToken(clientEditResponse.body());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private HttpResponse<String> requestNewSession() throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(URL_GET_NEW_SESSION)).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("New session response code: " + response.statusCode());
		saveResponseToFile(response.body(), "new-session.html");
		if (response.statusCode() != 200) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error creating new session, status code: " + response.statusCode());
		}
		return response;
	}

	private void requestLogin(String sessionCode, String formToken) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_POST_LOGIN))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.POST(HttpRequest.BodyPublishers.ofString(
						FORM_KEY_UTF8 + "=" + FORM_VALUE_UTF8 + "&" +
								FORM_KEY_TOKEN + "=" + formToken + "&" +
								FORM_KEY_REDIRECT + "=" + FORM_VALUE_REDIRECT + "&" +
								FORM_KEY_USERNAME + "=" + username + "&" +
								FORM_KEY_PASSWORD + "=" + password))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Login response code: " + response.statusCode());
		saveResponseToFile(response.body(), "login.html");
		if (response.statusCode() != 302) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error logging in, status code: " + response.statusCode());
		}
		String redirect = response.headers().firstValue("Location").orElse(null);
		System.out.println("Login redirect to " + redirect);
		if (redirect == null || !URL_GET_CART_AREA.equals(redirect)) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error login, wrong redirect, should redirect to /cart_area");
		}
	}

	private HttpResponse<String> requestClientEdit(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_CLIENT_EDIT))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Client edit response code: " + response.statusCode());
		saveResponseToFile(response.body(), "client-edit.html");
		if (response.statusCode() != 200) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error requesting client edit, status code: " + response.statusCode());
		}
		return response;
	}

	private String getSessionCode(List<String> cookies) {
		System.out.println("Response set-cookie: " + cookies);
		for (String c : cookies) {
			if (c.contains("_healcode_v3.0.1_session")) {
				String[] parts = c.split(";");
				for (String part : parts) {
					if (part.contains("_healcode_v3.0.1_session")) {
						String sessionCode = part.split("=")[1];
						System.out.println("Session Code: " + sessionCode);
						return sessionCode;
					}
				}
			}
		}
		throw new RuntimeException("Session code '_healcode_v3.0.1_session' not found in cookies");
	}

	private String getFormAuthenticityToken(String responseBody) {
		String token = null;
		String[] parts = responseBody.split("name=\"" + FORM_KEY_TOKEN + "\" value=\"");
		if (parts.length > 1) {
			token = parts[1].split("\"")[0];
		}
		if (token == null) {
			throw new RuntimeException("Form authenticity token not found in response body");
		}
		System.out.println("Form authenticity token: " + token);
		return token;
	}

	private String getLoginAuthenticityToken(String responseBody) {
		String token = null;
		String[] parts = responseBody.split("name=\"csrf-token\" content=\"");
		if (parts.length > 1) {
			token = parts[1].split("\"")[0];
		}
		if (token == null) {
			throw new RuntimeException("Login authenticity token not found in response body");
		}
		System.out.println("Login authenticity token: " + token);
		return token;
	}

	private void doBooking(String duration, String court, String date) {
		System.out.println("Start time [" + LocalDateTime.now() + "]");
		try {
			requestAddBooking(sessionCode, duration, court, date);
			String redirect = requestProceedToCheckout(sessionCode);
			if (URL_GET_SELECT_SERVICE.equals(redirect)) {
				requestSelectService(sessionCode);
				requestAddItem(sessionCode, duration);
				requestSelectPayment(sessionCode);
				requestProceedToCheckout(sessionCode);
			}
			HttpResponse<String> schedulesResponse = requestSchedules(sessionCode);
			validateBooking(schedulesResponse.body(), duration, court, date);
			requestLogout(sessionCode, loginToken);
		} catch (Exception e) {
			e.printStackTrace();
			if (sessionCode != null && loginToken != null) {
				try {
					requestLogout(sessionCode, loginToken);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		System.out.println("Finish time [" + LocalDateTime.now() + "]");
	}

	private void requestAddBooking(String sessionCode, String sessionType, String courtNumber, String startDateTime) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		String bookingUrl = URL_GET_ADD_BOOKING + "?" +
				"item%5Bmbo_location_id%5D=1&" +
				"item%5Bsession_type_id%5D=" + SESSION_TYPES.get(sessionType) + "&" +
				"item%5Bstaff_id%5D=" + COURT_NUMBERS.get(courtNumber) + "&" +
				"item%5Bstart_date_time%5D=" + startDateTime.replace(":", "%3A") + "&" +
				"item%5Btype%5D=Appointment&" +
				"item%5Bstaff_requested%5D=true&" +
				"item%5Bstaff_display_name%5D=Court+" + courtNumber + "+120a+Fullers";
		System.out.println("Booking URL: " + bookingUrl);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(bookingUrl))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Add booking response code: " + response.statusCode());
		saveResponseToFile(response.body(), "add-booking.html");
		if (response.statusCode() != 302) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error adding booking, status code: " + response.statusCode());
		}
		String redirect = response.headers().firstValue("Location").orElse(null);
		System.out.println("Add booking redirect to " + redirect);
		if (redirect == null || !URL_GET_CART.equals(redirect)) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error add booking, wrong redirect, should redirect to /cart");
		}
	}

	private String requestProceedToCheckout(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_PROCEED_TO_CHECKOUT))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Proceed to checkout response code: " + response.statusCode());
		saveResponseToFile(response.body(), "proceed-to-checkout.html");
		if (response.statusCode() == 200) {
			return null;
		} else if (response.statusCode() == 302) {
			String redirect = response.headers().firstValue("Location").orElse(null);
			System.out.println("Redirect URL: " + redirect);
			if (redirect == null) {
				System.err.println("Response body: " + response.body());
				throw new RuntimeException("Error proceeding to checkout, redirect URL not found");
			}
			if (redirect.equals(URL_GET_SELECT_SERVICE)) {
				return redirect;
			} else if (redirect.equals(URL_GET_CART)) {
				return redirect;
			} else if (redirect.contains(CHECKOUT_COMPLETE)) {
				return redirect;
			} else {
				System.err.println("Response body: " + response.body());
				throw new RuntimeException("Error proceeding to checkout, wrong redirect");
			}
		}
		System.err.println("Response body: " + response.body());
		throw new RuntimeException("Error proceeding to checkout, status code: " + response.statusCode());
	}

	private void requestSelectService(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_SELECT_SERVICE))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Select service response code: " + response.statusCode());
		saveResponseToFile(response.body(), "select-service.html");
		if (response.statusCode() != 200) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error selecting service, status code: " + response.statusCode());
		}
	}

	private void requestAddItem(String sessionCode, String duration) throws Exception{
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		String addItemUrl = URL_GET_ADD_ITEM + "?" +
				PARAM_UTF8 + "&" +
				FORM_KEY_MBO_ITEM + "=" + MBO_ITEMS.get(duration);
		System.out.println("Add item URL: " + addItemUrl);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(addItemUrl))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Select add item response code: " + response.statusCode());
		saveResponseToFile(response.body(), "add-item.html");
		if (response.statusCode() != 302) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error add item, status code: " + response.statusCode());
		}
		String redirect = response.headers().firstValue("Location").orElse(null);
		System.out.println("Add item redirect to " + redirect);
		if (redirect == null || !URL_GET_SELECT_PAYMENT.equals(redirect)) {
			System.err.println("Response body: " + response.body());
			throw new RuntimeException("Error add item, wrong redirect, should be /select_payment");
		}
	}

	private void requestSelectPayment(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_SELECT_PAYMENT))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Select payment response code: " + response.statusCode());
		saveResponseToFile(response.body(), "select-payment.html");
		if (response.statusCode() == 200) {
			return;
		}
		if (response.statusCode() == 302) {
			String redirect = response.headers().firstValue("Location").orElse(null);
			System.out.println("Select payment redirect to " + redirect);
			if (!URL_GET_CART.equals(redirect)) {
				System.out.println("Response body: " + response.body());
				throw new RuntimeException("Error selecting payment, wrong redirect, should redirect to /cart");
			}
		} else {
			System.out.println("Response body: " + response.body());
			throw new RuntimeException("Error selecting payment, status code: " + response.statusCode());
		}
	}

	private HttpResponse<String> requestSchedules(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_SCHEDULES))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Schedules response code: " + response.statusCode());
		saveResponseToFile(response.body(), "schedules.html");
		if (response.statusCode() != 200) {
			System.out.println("Response body: " + response.body());
			throw new RuntimeException("Error requesting schedules, status code: " + response.statusCode());
		}
		return response;
	}

	private void requestLogout(String sessionCode, String loginToken) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_POST_LOGOUT))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.POST(HttpRequest.BodyPublishers.ofString(
						FORM_KEY_TOKEN + "=" + loginToken + "&" +
								FORM_KEY_METHOD + "=" + FORM_VALUE_METHOD))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Logout response code: " + response.statusCode());
		saveResponseToFile(response.body(), "logout.html");
		if (response.statusCode() != 302) {
			System.out.println("Response body: " + response.body());
			throw new RuntimeException("Error logging out, status code: " + response.statusCode());
		}
		String redirect = response.headers().firstValue("Location").orElse(null);
		System.out.println("Logout redirect to " + redirect);
		if (redirect == null || !URL_GET_CART.equals(redirect)) {
			System.out.println("Response body: " + response.body());
			throw new RuntimeException("Error logout, wrong redirect, should redirect to /cart");
		}
	}

	private String requestCart(String sessionCode) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(URL_GET_CART))
				.header("Cookie", "_healcode_v3.0.1_session=" + sessionCode)
				.GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Cart Response Code: " + response.statusCode());
		saveResponseToFile(response.body(), "cart.html");
		if (response.statusCode() != 200) {
			throw new RuntimeException("Error requesting cart, status code: " + response.statusCode());
		}
		return response.body();
	}

	private void validateBooking(String responseBody, String sessionType, String courtNumber, String startDateTime) throws Exception {
		Date startDate = INPUT_DATE_FORMAT.parse(startDateTime);
		String date = CONFIRM_DATE_FORMAT.format(startDate);
		String time = getTime(startDate, sessionType);
		String duration = "Court Hire " + sessionType + " Minutes 120a Fullers Rd";
		String court = "with Court " + courtNumber;
		if (!responseBody.contains(date)) {
			throw new RuntimeException("Date not found in response body: " + date);
		}
		if (!responseBody.contains(time)) {
			throw new RuntimeException("Time not found in response body: " + time);
		}
		if (!responseBody.contains(duration)) {
			throw new RuntimeException("Dueation not found in response body: " + duration);
		}
		if (!responseBody.contains(court)) {
			throw new RuntimeException("Court not found in response body: " + court);
		}
	}

	private String getTime(Date startDate, String sessionType) {
		Date endDate = new Date(startDate.getTime() + Integer.parseInt(sessionType) * 60 * 1000);
		String endTime = CONFIRM_TIME_FORMAT.format(endDate);
		return (endTime + " AEST").toUpperCase();
	}

	private void waitForMidnight() throws Exception {
		boolean shouldWait = true;
		while (shouldWait) {
			long secondsToMidnight = getSecondsToMidnight();
			if (secondsToMidnight / 3600 > 0) {
				System.out.println("Sleep 1 hour");
				Thread.sleep(3600 * 1000);
			} else if (secondsToMidnight / 600 > 0) {
				System.out.println("Sleep 10 minutes");
				Thread.sleep(600 * 1000);
			} else if (secondsToMidnight / 60 > 0) {
				preLogin();
				System.out.println("Sleep 1 minute");
				Thread.sleep(60 * 1000);
			} else {
				preLogin();
				System.out.println("Sleep " + secondsToMidnight + " seconds");
				Thread.sleep(secondsToMidnight * 1000);
				shouldWait = false;
			}
		}
	}

	private long getSecondsToMidnight() {
		LocalDateTime now = LocalDateTime.now();
		System.out.println("Now [" + now + "]");
		LocalDateTime midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).plusDays(1);
		long secondsToMidnight = Duration.between(now, midnight).getSeconds() + 1;
		System.out.println("Seconds to midnight [" + secondsToMidnight + "]");
		return secondsToMidnight;
	}

	private void saveResponseToFile(String response, String filePath) {
		try {
			Path path = Paths.get(filePath);
			Files.writeString(path, response, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("Save response to file [" + filePath + "] failed");
			e.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println("Usage - java -jar target/mind-body-online-0.0.1-SNAPSHOT.jar duration court datetime");
		System.err.println("    - duration: 30, 60, 90");
		System.err.println("    - court:    1, 2, 3, 4, 5, 6");
		System.err.println("    - datetime: 2025-04-15T09:30:00");
	}

	private void printParameters(String[] args) {
		System.out.println("Duration [" + args[0] + "]");
		System.out.println("Court    [" + args[1] + "]");
		System.out.println("Time     [" + args[2] + "]");
	}

	/**
	 * Default parameters are 90 minutes, court 5, and 8 am the week after next week's Saturday
	 *
	 * @return
	 */
	private String[] getDefaultParameters() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextSaturday = now
				.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
				.withHour(8)
				.withMinute(0)
				.withSecond(0)
				.withNano(0)
				.plusDays(7);
		String date = nextSaturday.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		return new String[] {"90", "5", date};
	}

}
