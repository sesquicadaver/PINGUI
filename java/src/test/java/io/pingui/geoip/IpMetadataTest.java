package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpMetadataTest {
    @Test
    void unknownRequiresOnlyIpScopeAndNoneSource() {
        IpMetadata meta = IpMetadata.unknown("8.8.8.8", IpAddressScope.PUBLIC);
        assertEquals("8.8.8.8", meta.ip());
        assertEquals(IpAddressScope.PUBLIC, meta.scope());
        assertEquals(IpMetadataSource.NONE, meta.source());
        assertNull(meta.countryIso());
        assertNull(meta.asn());
        assertFalse(meta.hasCoordinates());
        assertFalse(meta.hasCountry());
        assertFalse(meta.hasAsn());
    }

    @Test
    void enrichedFieldsAreNullableAndNormalized() {
        IpMetadata meta = new IpMetadata(
                " 2001:4860:4860::8888 ",
                IpAddressScope.PUBLIC,
                " us ",
                " CA ",
                " Mountain View ",
                37.386,
                -122.084,
                100.0,
                15169,
                " Google ",
                IpMetadataSource.MMDB,
                1_700_000_000L);
        assertEquals("2001:4860:4860::8888", meta.ip());
        assertEquals("US", meta.countryIso());
        assertEquals("CA", meta.subdivision());
        assertEquals("Mountain View", meta.city());
        assertEquals("Google", meta.organization());
        assertTrue(meta.hasCoordinates());
        assertTrue(meta.hasCountry());
        assertTrue(meta.hasAsn());
    }

    @Test
    void noneSourceRejectsPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpMetadata(
                        "8.8.8.8",
                        IpAddressScope.PUBLIC,
                        "US",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        IpMetadataSource.NONE,
                        null));
    }

    @Test
    void rejectsPartialCoordinatesAndBadIso() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpMetadata(
                        "8.8.8.8",
                        IpAddressScope.PUBLIC,
                        null,
                        null,
                        null,
                        1.0,
                        null,
                        null,
                        null,
                        null,
                        IpMetadataSource.OVERRIDE,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpMetadata(
                        "8.8.8.8",
                        IpAddressScope.PUBLIC,
                        "USA",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        IpMetadataSource.OVERRIDE,
                        null));
    }

    @Test
    void rejectsBlankIpAndNegativeAsn() {
        assertThrows(IllegalArgumentException.class, () -> IpMetadata.unknown(" ", IpAddressScope.PUBLIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IpMetadata(
                        "8.8.8.8",
                        IpAddressScope.PUBLIC,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        -1,
                        null,
                        IpMetadataSource.OVERRIDE,
                        null));
    }
}
