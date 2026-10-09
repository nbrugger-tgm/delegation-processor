package com.example.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Generated Packager delegator (package delegation)")
public class PackagerTest {
    class TestPackager implements Packager {
        private final Path path;

        TestPackager(Path path) {
            this.path = path;
        }

        @Override
        public Path pack() {
            return path;
        }
    }
    class PackagerFacade implements PackagerDelegator {
        private final Packager delegate;

        public PackagerFacade(Packager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Packager getPackagerDelegate() {
            return delegate;
        }
    }

    @Test
    @DisplayName("forwards to the delegate")
    void forwardsToDelegate() {
        Packager packager = new PackagerFacade(new TestPackager(Path.of("test")));

        assertThat(packager.pack()).isEqualTo(Path.of("test"));
    }
}
