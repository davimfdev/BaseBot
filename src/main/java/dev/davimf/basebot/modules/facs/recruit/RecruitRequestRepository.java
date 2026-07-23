package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Solicitações de Set (BOTSPECS Módulo 4), backed pela tabela {@code recruit_request}. */
public final class RecruitRequestRepository {

    /** Uma solicitação persistida. */
    public record Request(String applicantId, String recruiterId, String idJogo,
                          String nome, String telefone, String status) {}

    private final SqliteManager sqlite;

    public RecruitRequestRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void save(String messageId, String guildId, String applicantId, String recruiterId,
                     String idJogo, String nome, String telefone) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO recruit_request
                         (message_id, guild_id, applicant_id, recruiter_id, id_jogo, nome, telefone)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, messageId);
            ps.setString(2, guildId);
            ps.setString(3, applicantId);
            ps.setString(4, recruiterId);
            ps.setString(5, idJogo);
            ps.setString(6, nome);
            ps.setString(7, telefone);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("save recruit_request " + messageId, e);
        }
    }

    public Optional<Request> find(String messageId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT applicant_id, recruiter_id, id_jogo, nome, telefone, status
                     FROM recruit_request WHERE message_id = ?
                     """)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Request(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find recruit_request " + messageId, e);
        }
    }

    /** Claim atômico: muda só PENDING -> status. Retorna true se venceu. */
    public boolean resolvePending(String messageId, String status) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE recruit_request SET status = ? WHERE message_id = ? AND status = 'PENDING'")) {
            ps.setString(1, status);
            ps.setString(2, messageId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("resolvePending recruit_request " + messageId, e);
        }
    }

    /** Muda o status incondicionalmente (usado só para reverter para PENDING). */
    public void setStatus(String messageId, String status) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE recruit_request SET status = ? WHERE message_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("setStatus recruit_request " + messageId, e);
        }
    }
}
