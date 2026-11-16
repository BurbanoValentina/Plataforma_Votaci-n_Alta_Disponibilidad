package com.voting.platform.archiver;

import com.voting.platform.archiver.service.AnalyticsArchiverService;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.VoteAuditRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnalyticsArchiverService - Audit Persistence Unit Tests (AAA Pattern)")
class AnalyticsArchiverServiceTest {

    private static class StubVoteAuditRepository implements VoteAuditRepositoryPort {
        private final List<VoteRecord> savedVotes = new ArrayList<>();

        @Override
        public void saveAudit(VoteRecord vote) {
            savedVotes.add(vote);
        }

        @Override
        public long count() {
            return savedVotes.size();
        }

        public List<VoteRecord> getSavedVotes() {
            return savedVotes;
        }
    }

    private StubVoteAuditRepository auditRepository;
    private AnalyticsArchiverService service;

    @BeforeEach
    void setUp() {
        auditRepository = new StubVoteAuditRepository();
        service = new AnalyticsArchiverService(auditRepository);
    }

    @Test
    @DisplayName("Arrange valid vote -> Act archiveVote -> Assert transactional audit persisted via DIP port")
    void shouldPersistVoteAuditRecord() {
        // Arrange
        VoteRecord vote = new VoteRecord(
            VoterId.of("voter_001"),
            CandidateId.of("cand_A"),
            Region.NORTE,
            IpAddress.of("192.168.1.1"),
            Instant.now()
        );

        // Act
        service.archiveVote(vote);

        // Assert
        assertThat(service.getArchivedVoteCount()).isEqualTo(1);
        VoteRecord saved = auditRepository.getSavedVotes().get(0);
        assertThat(saved.voterId().value()).isEqualTo("voter_001");
        assertThat(saved.candidateId().value()).isEqualTo("cand_A");
        assertThat(saved.region()).isEqualTo(Region.NORTE);
    }
}
