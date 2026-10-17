package dao;

import dto.UserDTO;
import enums.Role;
import java.sql.*;
import java.util.*;
import utills.DBConnection;
import utills.PasswordUtil;

public class UserDAO implements AutoCloseable {

    private final Connection connection;
    private final String SELECT = "SELECT user_id, user_name, password, full_name, role, is_active, email, phone_number FROM tblUser";

    public UserDAO() throws SQLException, ClassNotFoundException {
        this(DBConnection.getConnection());
    }

    public UserDAO(Connection connection) {
        this.connection = connection;
    }

    private final String SELECT_PROFILE = "SELECT user_id, user_name, full_name, role, is_active, email, phone_number FROM tblUser";

    private UserDTO map(ResultSet rs) throws SQLException {
        return map(rs, true);
    }

    private UserDTO map(ResultSet rs, boolean includePassword) throws SQLException {
        return new UserDTO(rs.getString("user_id"), rs.getString("user_name"),
                includePassword ? rs.getString("password") : null, rs.getString("full_name"),
                Role.valueOf(rs.getString("role")), rs.getBoolean("is_active"), rs.getString("email"),
                rs.getString("phone_number"));
    }

    private UserDTO one(String column, String value) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public UserDTO findById(String id) throws SQLException {
        return one("user_id", id);
    }

    public UserDTO findByUserName(String name) throws SQLException {
        return one("user_name", name);
    }

    public List<UserDTO> findAll() throws SQLException {
        List<UserDTO> users = new ArrayList<>();
        try ( PreparedStatement ps = connection.prepareStatement(SELECT_PROFILE + " ORDER BY user_id");  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs, false));
            }
        }
        return users;
    }

    public boolean insert(UserDTO user) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tblUser(user_id,user_name,password,full_name,role,is_active,email,phone_number) VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUserName());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getRole().name());
            ps.setBoolean(6, user.isActive());
            ps.setString(7, user.getEmail());
            ps.setString(8, user.getPhone());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean update(UserDTO user) throws SQLException {
        return managedChange(user, "ALL");
    }

    public boolean updateDetails(UserDTO user) throws SQLException {
        return managedChange(user, "DETAILS");
    }

    public boolean updateRole(UserDTO user) throws SQLException {
        return managedChange(user, "ROLE");
    }

    public boolean updateActive(UserDTO user) throws SQLException {
        return managedChange(user, "ACTIVE");
    }

    public boolean resetPassword(String id, String hash) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement("UPDATE tblUser SET password=? WHERE user_id=?")) {
            ps.setString(1, hash);
            ps.setString(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Soft deletion preserves FK references/history and revokes Viewer
     * permissions.
     */
    public boolean delete(String id) throws SQLException {
        UserDTO user = findById(id);
        if (user == null) {
            return false;
        }
        user.setActive(false);
        return managedChange(user, "DELETE");
    }

    private boolean managedChange(UserDTO user, String mode) throws SQLException {
        boolean deleting = "DELETE".equals(mode), all = "ALL".equals(mode), details = "DETAILS".equals(mode),
                role = "ROLE".equals(mode), active = "ACTIVE".equals(mode);
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            // Serializes changes to the active Admin set, preventing deletion/demotion of
            // the last Admin.
            int admins = 0;
            boolean targetAdmin = false;
            try ( PreparedStatement ps = connection.prepareStatement(
                    "SELECT user_id FROM tblUser WITH (UPDLOCK,HOLDLOCK) WHERE role='ADMIN' AND is_active=1");  ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    admins++;
                    if (user.getUserId().equals(rs.getString("user_id"))) {
                        targetAdmin = true;
                    }
                }
            }
            boolean deactivating = deleting || (active || all) && !user.isActive();
            boolean demoting = (role || details || all) && user.getRole() != Role.ADMIN;
            if (targetAdmin && admins <= 1 && (deactivating || demoting)) {
                throw new IllegalArgumentException("The last active Admin cannot be disabled, deleted or assigned another role.");
            }
            boolean changed;
            String sql = deleting ? "UPDATE tblUser SET is_active=0 WHERE user_id=?"
                    : role ? "UPDATE tblUser SET role=? WHERE user_id=?"
                            : active ? "UPDATE tblUser SET is_active=? WHERE user_id=?"
                                    : details
                                            ? "UPDATE tblUser SET full_name=?,role=?,email=?,phone_number=? WHERE user_id=?"
                                            : "UPDATE tblUser SET user_name=?,password=?,full_name=?,role=?,is_active=?,email=?,phone_number=? WHERE user_id=?";
            try ( PreparedStatement ps = connection.prepareStatement(sql)) {
                if (deleting) {
                    ps.setString(1, user.getUserId());
                } else if (role) {
                    ps.setString(1, user.getRole().name());
                    ps.setString(2, user.getUserId());
                } else if (active) {
                    ps.setBoolean(1, user.isActive());
                    ps.setString(2, user.getUserId());
                } else if (details) {
                    ps.setString(1, user.getFullName());
                    ps.setString(2, user.getRole().name());
                    ps.setString(3, user.getEmail());
                    ps.setString(4, user.getPhone());
                    ps.setString(5, user.getUserId());
                } else {
                    ps.setString(1, user.getUserName());
                    ps.setString(2, user.getPassword());
                    ps.setString(3, user.getFullName());
                    ps.setString(4, user.getRole().name());
                    ps.setBoolean(5, user.isActive());
                    ps.setString(6, user.getEmail());
                    ps.setString(7, user.getPhone());
                    ps.setString(8, user.getUserId());
                }
                changed = ps.executeUpdate() == 1;
            }
            if (changed && (deactivating || (all || role || details) && user.getRole() != Role.VIEWER)) {
                try ( PreparedStatement ps = connection
                        .prepareStatement("UPDATE tblDevice_Permission SET is_active=0 WHERE user_id=?")) {
                    ps.setString(1, user.getUserId());
                    ps.executeUpdate();
                }
            }
            connection.commit();
            return changed;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    public boolean updateProfile(String id, String name, String email, String phone) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement(
                "UPDATE tblUser SET full_name=?,email=?,phone_number=? WHERE user_id=? AND is_active=1")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, id);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean updatePassword(String id, String oldHash, String newHash) throws SQLException {
        try ( PreparedStatement ps = connection
                .prepareStatement("UPDATE tblUser SET password=? WHERE user_id=? AND password=? AND is_active=1")) {
            ps.setString(1, newHash);
            ps.setString(2, id);
            ps.setString(3, oldHash);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Verifies credentials; the controller must check isActive before creating
     * a login session.
     */
    public UserDTO checkLogin(String name, String password) throws SQLException {
        UserDTO user = findByUserName(name);
        return user != null && PasswordUtil.verify(password, user.getPassword()) ? user : null;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

}
