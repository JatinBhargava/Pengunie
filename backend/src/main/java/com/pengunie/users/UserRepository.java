package com.pengunie.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

	public record UserCredentials(UUID id, String email, String passwordHash, String status) {
	}

	private final JdbcClient jdbc;

	UserRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean emailExists(String email) {
		return jdbc.sql("SELECT count(*) FROM users WHERE lower(email) = lower(:email)")
			.param("email", email)
			.query(Long.class)
			.single() > 0;
	}

	public UUID create(String email, String passwordHash, String displayName, String timezone) {
		UUID id = jdbc.sql("INSERT INTO users (email, password_hash) VALUES (:email, :hash) RETURNING id")
			.param("email", email)
			.param("hash", passwordHash)
			.query(UUID.class)
			.single();
		jdbc.sql("INSERT INTO user_profiles (user_id, display_name, timezone) VALUES (:id, :name, :tz)")
			.param("id", id)
			.param("name", displayName)
			.param("tz", timezone)
			.update();
		return id;
	}

	public Optional<UserCredentials> findCredentialsByEmail(String email) {
		return jdbc.sql("SELECT id, email, password_hash, status FROM users WHERE lower(email) = lower(:email)")
			.param("email", email)
			.query(UserCredentials.class)
			.optional();
	}

	public Optional<Profile> findProfile(UUID userId) {
		return jdbc.sql("""
				SELECT u.id, u.email, p.display_name, p.timezone, p.locale
				FROM users u JOIN user_profiles p ON p.user_id = u.id WHERE u.id = :id
				""")
			.param("id", userId)
			.query(Profile.class)
			.optional();
	}

	public void updateProfile(UUID userId, String displayName, String timezone, String locale) {
		jdbc.sql("""
				UPDATE user_profiles SET display_name = :name, timezone = :tz, locale = :locale, updated_at = now()
				WHERE user_id = :id
				""")
			.param("id", userId)
			.param("name", displayName)
			.param("tz", timezone)
			.param("locale", locale)
			.update();
	}

}
