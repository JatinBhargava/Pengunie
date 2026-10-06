package com.pengunie.users;

import java.util.UUID;

public record Profile(UUID id, String email, String displayName, String timezone, String locale) {
}
