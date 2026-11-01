package art.pilipala.api.auth.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.auth.domain.User;

@Mapper
public interface UserMapper {

    String COLS = "id, username, phone, email, password_hash, locale, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT " + COLS + " FROM users WHERE phone = #{phone}")
    User findByPhone(@Param("phone") String phone);

    @Select("SELECT " + COLS + " FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT " + COLS + " FROM users WHERE id = #{id}")
    User findById(@Param("id") UUID id);

    @Select("SELECT count(*) FROM users WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Select("SELECT count(*) FROM users WHERE phone = #{phone}")
    int countByPhone(@Param("phone") String phone);

    @Select("SELECT count(*) FROM users WHERE email = #{email}")
    int countByEmail(@Param("email") String email);

    @Select("INSERT INTO users (phone, email, password_hash, locale) VALUES "
            + "(#{phone,jdbcType=VARCHAR}, #{email,jdbcType=VARCHAR}, #{passwordHash}, #{locale,jdbcType=VARCHAR}) RETURNING " + COLS)
    User insertIdentity(@Param("phone") String phone, @Param("email") String email,
            @Param("passwordHash") String passwordHash, @Param("locale") String locale);

    @Update("UPDATE users SET phone = #{phone}, updated_at = now() WHERE id = #{id}")
    int updatePhone(@Param("id") UUID id, @Param("phone") String phone);

    @Update("UPDATE users SET email = #{email}, updated_at = now() WHERE id = #{id}")
    int updateEmail(@Param("id") UUID id, @Param("email") String email);

    @Update("UPDATE users SET password_hash = #{passwordHash}, updated_at = now() WHERE id = #{id}")
    int updatePasswordHash(@Param("id") UUID id, @Param("passwordHash") String passwordHash);
}
