package br.com.mercadinhos.catalogo.internal;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class PrecificacaoMigrationTests {
    @Test
    void migraV7SemInventarCustosEImpedePrecificacaoInconsistente() {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).target("7").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (1, 'Existente')");
            jdbc.update("INSERT INTO usuarios (id, nome, email, senha_hash) VALUES (1, 'Admin', 'fixture@example.com', 'fixture')");
            jdbc.update("INSERT INTO categorias (id, organizacao_id, nome) VALUES (1, 1, 'Existente')");
            jdbc.update("""
                    INSERT INTO produtos (id, organizacao_id, categoria_id, nome, codigo_barras, unidade_medida,
                    criado_em, atualizado_em, criado_por, atualizado_por) VALUES
                    (1, 1, 1, 'Produto existente', '000123', 'UN', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, 1)
                    """);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT codigo_barras FROM produtos WHERE id = 1", String.class)).isEqualTo("000123");
            for (String coluna : new String[]{"valor_compra", "percentual_markup", "preco_venda_padrao"}) {
                assertThat(jdbc.queryForObject("SELECT " + coluna + " FROM produtos WHERE id = 1", BigDecimal.class)).isNull();
            }
            assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET valor_compra = 10 WHERE id = 1")).isInstanceOf(DataAccessException.class);
            jdbc.update("UPDATE produtos SET valor_compra = 0.10, percentual_markup = 5, preco_venda_padrao = 0.11 WHERE id = 1");
            assertThat(jdbc.queryForObject("SELECT preco_venda_padrao FROM produtos WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("0.11");
            for (String atribuicao : new String[]{"valor_compra = -1", "valor_compra = 0", "percentual_markup = -1",
                    "percentual_markup = 10001", "preco_venda_padrao = 0.10", "preco_venda_padrao = -1", "percentual_markup = NULL"}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET " + atribuicao + " WHERE id = 1")).isInstanceOf(DataAccessException.class);
            }
            jdbc.update("UPDATE produtos SET preco_venda_padrao = NULL WHERE id = 1");
            jdbc.update("UPDATE produtos SET percentual_markup = 0 WHERE id = 1");
            assertThat(jdbc.queryForObject("SELECT valor_compra FROM produtos WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("0.10");
        }
    }
}
