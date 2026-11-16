package com.voting.platform.domain;

import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Value Objects & Records - Domain Unit Tests")
class ValueObjectsTest {

    @Nested
    @DisplayName("VoterId Validations (AAA Pattern)")
    class VoterIdTests {

        @ParameterizedTest
        @ValueSource(strings = {"user123", "usr_99", "voter-001", "ABC_XYZ-123"})
        @DisplayName("Arrange valid string -> Act instantiate VoterId -> Assert value preserved")
        void shouldCreateValidVoterId(String validId) {
            // Arrange & Act
            VoterId voterId = VoterId.of(validId);

            // Assert
            assertThat(voterId.value()).isEqualTo(validId);
            assertThat(voterId.toString()).isEqualTo(validId);
        }

        @Test
        @DisplayName("Arrange null -> Act instantiate VoterId -> Assert NullPointerException")
        void shouldRejectNullVoterId() {
            assertThatThrownBy(() -> new VoterId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Voter ID cannot be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "ab", "usr@name", "voter#1!", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
        @DisplayName("Arrange invalid strings -> Act instantiate VoterId -> Assert IllegalArgumentException")
        void shouldRejectInvalidVoterId(String invalidId) {
            assertThatThrownBy(() -> new VoterId(invalidId))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CandidateId Validations (AAA Pattern)")
    class CandidateIdTests {

        @ParameterizedTest
        @ValueSource(strings = {"candidate_1", "cand-A", "C1", "JOHN_DOE"})
        @DisplayName("Arrange valid candidate -> Act instantiate -> Assert valid")
        void shouldCreateValidCandidateId(String validId) {
            CandidateId candidateId = CandidateId.of(validId);
            assertThat(candidateId.value()).isEqualTo(validId);
        }

        @Test
        @DisplayName("Arrange null candidate -> Act instantiate -> Assert NullPointerException")
        void shouldRejectNullCandidateId() {
            assertThatThrownBy(() -> new CandidateId(null))
                .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "C", "c@ndidate"})
        @DisplayName("Arrange invalid candidate -> Act instantiate -> Assert IllegalArgumentException")
        void shouldRejectInvalidCandidateId(String invalidId) {
            assertThatThrownBy(() -> new CandidateId(invalidId))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("IpAddress Validations (AAA Pattern)")
    class IpAddressTests {

        @ParameterizedTest
        @ValueSource(strings = {"192.168.1.1", "10.0.0.1", "127.0.0.1", "255.255.255.255", "localhost", "::1"})
        @DisplayName("Arrange valid IP/host -> Act instantiate -> Assert valid")
        void shouldAcceptValidIp(String validIp) {
            IpAddress ip = IpAddress.of(validIp);
            assertThat(ip.value()).isEqualTo(validIp);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "999.999.999.999", "192.168.1", "abc.def.ghi.jkl", "192.168.1.256"})
        @DisplayName("Arrange invalid IP -> Act instantiate -> Assert IllegalArgumentException")
        void shouldRejectInvalidIp(String invalidIp) {
            assertThatThrownBy(() -> new IpAddress(invalidIp))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Region Enum Validations")
    class RegionTests {

        @Test
        @DisplayName("Should parse case-insensitive region and map routing keys")
        void shouldParseRegionAndMapRoutingKey() {
            // Arrange & Act
            Region norte = Region.fromString("norte");
            Region sur = Region.fromString("SUR");
            Region centro = Region.fromString("Centro");

            // Assert
            assertThat(norte).isEqualTo(Region.NORTE);
            assertThat(norte.toRoutingKey()).isEqualTo("results.norte");
            assertThat(sur).isEqualTo(Region.SUR);
            assertThat(sur.toRoutingKey()).isEqualTo("results.sur");
            assertThat(centro).isEqualTo(Region.CENTRO);
            assertThat(centro.toRoutingKey()).isEqualTo("results.centro");
        }

        @Test
        @DisplayName("Should reject unknown region")
        void shouldRejectUnknownRegion() {
            assertThatThrownBy(() -> Region.fromString("ANTARTICA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown region");
        }
    }

    @Nested
    @DisplayName("VoteRecord Invariants")
    class VoteRecordTests {

        @Test
        @DisplayName("Should instantiate immutable vote record and default timestamp")
        void shouldInstantiateVoteRecord() {
            // Arrange
            VoterId voterId = VoterId.of("voter_1");
            CandidateId candidateId = CandidateId.of("candidate_A");
            Region region = Region.NORTE;
            IpAddress ip = IpAddress.of("192.168.1.50");

            // Act
            VoteRecord vote = new VoteRecord(voterId, candidateId, region, ip, null);

            // Assert
            assertThat(vote.voterId()).isEqualTo(voterId);
            assertThat(vote.candidateId()).isEqualTo(candidateId);
            assertThat(vote.region()).isEqualTo(region);
            assertThat(vote.ipAddress()).isEqualTo(ip);
            assertThat(vote.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should reject null fields in VoteRecord")
        void shouldRejectNullFields() {
            VoterId validVoter = VoterId.of("voter_01");
            CandidateId validCandidate = CandidateId.of("cand_01");
            IpAddress validIp = IpAddress.of("127.0.0.1");

            assertThatThrownBy(() -> new VoteRecord(null, validCandidate, Region.NORTE, validIp, Instant.now()))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new VoteRecord(validVoter, null, Region.NORTE, validIp, Instant.now()))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new VoteRecord(validVoter, validCandidate, null, validIp, Instant.now()))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new VoteRecord(validVoter, validCandidate, Region.NORTE, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
