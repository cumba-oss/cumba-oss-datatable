package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversionContextTest
{

    @Test
    void exposesSinksOptionsAndMinter()
    {
        List<String> log = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        OidMinter minter = new OidMinter(Set.of());
        ConversionContext ctx = new ConversionContext(log, warn, true, "Other", minter);

        ctx.log("a");
        ctx.warn("b");

        assertEquals(List.of("a"), log);
        assertEquals(List.of("b"), warn);
        assertTrue(ctx.keepLegacyStandardAttributes());
        assertEquals("Other", ctx.context());
        assertEquals("WC.1", ctx.oids().mint("WC"));
    }

}
