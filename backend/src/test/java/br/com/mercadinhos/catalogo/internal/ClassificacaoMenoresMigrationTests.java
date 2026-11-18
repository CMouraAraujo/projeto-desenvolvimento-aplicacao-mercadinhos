package br.com.mercadinhos.catalogo.internal;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.*;

class ClassificacaoMenoresMigrationTests {
    @Test
    void migraDadosExistentesDaV5SemPerdasEImpedeNulosOuValoresNaoBooleanos() {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (1, 'Organização existente')");
            jdbc.update("INSERT INTO usuarios (id, nome, email, senha_hash) VALUES (1, 'Admin', 'fixture@example.com', 'hash-de-fixture')");
            jdbc.update("INSERT INTO categorias (id, organizacao_id, nome) VALUES (1, 1, 'Categoria existente')");
            jdbc.update("""
                    INSERT INTO produtos (id, organizacao_id, categoria_id, nome, codigo_barras, unidade_medida,
                    criado_em, atualizado_em, criado_por, atualizado_por) VALUES
                    (1, 1, 1, 'Produto existente', '000123', 'UN', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, 1)
                    """);

            Flyway.configure().dataSource(source).target("6").load().migrate();
            jdbc.update("UPDATE categorias SET permitido_para_menores = true WHERE id = 1");
            Flyway.configure().dataSource(source).load().migrate();
            jdbc.update("INSERT INTO categorias (id, organizacao_id, nome) VALUES (2, 1, 'Nova categoria livre')");
            jdbc.update("""
                    INSERT INTO produtos (id, organizacao_id, categoria_id, nome, codigo_barras, unidade_medida,
                    criado_em, atualizado_em, criado_por, atualizado_por) VALUES
                    (2, 1, 2, 'Novo produto livre', '000124', 'UN', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, 1)
                    """);
            assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM categorias WHERE id = 2", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM produtos WHERE id = 2", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT codigo_barras FROM produtos WHERE id = 1", String.class)).isEqualTo("000123");
            assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM produtos WHERE id = 1", Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM categorias WHERE id = 1", Boolean.class)).isTrue();
            for (String tabela : new String[] {"categorias", "produtos"}) {
                boolean anterior = jdbc.queryForObject("SELECT permitido_para_menores FROM " + tabela + " WHERE id = 1", Boolean.class);
                assertThatThrownBy(() -> jdbc.update("UPDATE " + tabela + " SET permitido_para_menores = NULL WHERE id = 1"))
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertThatThrownBy(() -> jdbc.update("UPDATE " + tabela + " SET permitido_para_menores = 2 WHERE id = 1"))
                        .isInstanceOf(DataAccessException.class).rootCause().isInstanceOf(SQLException.class)
                        .extracting(error -> ((SQLException) error).getErrorCode()).isEqualTo(3819);
                assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM " + tabela + " WHERE id = 1", Boolean.class)).isEqualTo(anterior);
            }
        }
    }
}
