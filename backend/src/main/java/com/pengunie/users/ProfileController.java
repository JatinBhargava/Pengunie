package com.pengunie.users;

import java.util.IllformedLocaleException;
import java.util.Locale;

import com.pengunie.audit.AuditService;
import com.pengunie.common.AccessScope;
import com.pengunie.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class ProfileController {

	private final UserRepository users;

	private final AuditService audit;

	ProfileController(UserRepository users, AuditService audit) {
		this.users = users;
		this.audit = audit;
	}

	record UpdateProfileRequest(@NotBlank @Size(max = 120) String displayName, @NotBlank String timezone,
			@NotBlank @Size(max = 20) String locale) {
	}

	@GetMapping
	Profile get(AccessScope scope) {
		return users.findProfile(scope.userId()).orElseThrow(() -> ApiException.notFound("Profile"));
	}

	@PutMapping
	Profile update(AccessScope scope, @Valid @RequestBody UpdateProfileRequest request) {
		String timezone = Timezones.validate(request.timezone());
		String locale = validLocale(request.locale());
		users.updateProfile(scope.userId(), request.displayName().strip(), timezone, locale);
		audit.record(scope.userId(), "PROFILE_UPDATED", "user", scope.userId());
		return get(scope);
	}

	private static String validLocale(String locale) {
		try {
			return new Locale.Builder().setLanguageTag(locale).build().toLanguageTag();
		}
		catch (IllformedLocaleException ex) {
			throw ApiException.badRequest("invalid_locale", "Invalid locale: " + locale);
		}
	}

}
