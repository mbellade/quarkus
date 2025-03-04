package io.quarkus.test.devui;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

public class DevUIHibernateOrmSmokeTest extends AbstractDevUIHibernateOrmTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar.addAsResource(
                    new StringAsset("quarkus.datasource.db-kind=h2\n"
                            + "quarkus.datasource.jdbc.url=jdbc:h2:mem:test\n"
                            + "quarkus.hibernate-orm.database.generation=drop-and-create\n"),
                    "application.properties")
                    .addAsResource(new StringAsset("insert into MyEntity(id, field) VALUES (1, 'entity_1');\n"), "import.sql")
                    .addClasses(MyEntity.class));

    public DevUIHibernateOrmSmokeTest() {
        super("<default>", "MyEntity", "io.quarkus.test.devui.MyEntity", 1);
    }
}
