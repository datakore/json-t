package io.github.datakore.jsont.datagen;

import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.util.CollectionUtils;
import org.instancio.Instancio;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UserGenerator implements DataGenerator<User> {
    private static final int POOL_SIZE = 10;
    private static final List<User> USER_POOL = new ArrayList<>(POOL_SIZE);
    private final SecureRandom random = new SecureRandom();

    public void initialize() {
        System.out.println("Pre-generating 10K orders...");
        Instant start = Instant.now();

        for (int i = 0; i < POOL_SIZE; i++) {
            User user = Instancio.of(User.class).create();
            USER_POOL.add(user);
        }
    }

    @Override
    public User generate(String schema) {
        if (CollectionUtils.isEmpty(USER_POOL)) {
            return Instancio.of(User.class).create();
        }
        return USER_POOL.get(random.nextInt(POOL_SIZE));
    }
}
