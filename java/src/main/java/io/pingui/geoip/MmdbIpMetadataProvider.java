package io.pingui.geoip;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Offline MaxMind MMDB provider for City/Country (+ optional ASN) (P36-005).
 *
 * <p>Uses the official {@link DatabaseReader}. Does not perform network I/O. SPECIAL addresses are
 * skipped (return {@code null}) so documentation/CGNAT never get a country from MMDB. Unknown
 * public IPs return {@link IpMetadataSource#NONE}.
 *
 * <p>Callers must {@link #close()} when the provider is discarded (atomic reload lands in P36-006).
 */
public final class MmdbIpMetadataProvider implements IpMetadataProvider, Closeable {
    private final DatabaseReader geoReader;
    private final DatabaseReader asnReader;
    private final GeoMode geoMode;
    private final MmdbDatabaseInfo geoInfo;
    private final MmdbDatabaseInfo asnInfo;

    private MmdbIpMetadataProvider(
            DatabaseReader geoReader,
            DatabaseReader asnReader,
            GeoMode geoMode,
            MmdbDatabaseInfo geoInfo,
            MmdbDatabaseInfo asnInfo) {
        this.geoReader = geoReader;
        this.asnReader = asnReader;
        this.geoMode = geoMode;
        this.geoInfo = geoInfo;
        this.asnInfo = asnInfo;
    }

    /**
     * Open a City or Country MMDB (no ASN).
     *
     * @throws IllegalArgumentException when the file is not a City/Country edition
     * @throws IllegalStateException when the file cannot be read
     */
    public static MmdbIpMetadataProvider open(Path geoDb) {
        return open(geoDb, null);
    }

    /**
     * Open a City or Country MMDB plus optional ASN MMDB.
     *
     * @param asnDb optional GeoLite2/GeoIP2 ASN database; {@code null} to skip ASN
     */
    public static MmdbIpMetadataProvider open(Path geoDb, Path asnDb) {
        if (geoDb == null || !Files.isRegularFile(geoDb)) {
            throw new IllegalArgumentException("GeoIP MMDB file is required: " + geoDb);
        }
        DatabaseReader geoReader = openReader(geoDb);
        MmdbDatabaseInfo geoInfo = infoOf(geoReader);
        GeoMode mode = GeoMode.fromType(geoInfo.databaseType());
        DatabaseReader asnReader = null;
        MmdbDatabaseInfo asnInfo = null;
        if (asnDb != null) {
            if (!Files.isRegularFile(asnDb)) {
                closeQuietly(geoReader);
                throw new IllegalArgumentException("ASN MMDB file not found: " + asnDb);
            }
            try {
                asnReader = openReader(asnDb);
                asnInfo = infoOf(asnReader);
                if (!asnInfo.databaseType().toLowerCase().contains("asn")) {
                    closeQuietly(asnReader);
                    closeQuietly(geoReader);
                    throw new IllegalArgumentException(
                            "Expected ASN MMDB, got database type: " + asnInfo.databaseType());
                }
            } catch (RuntimeException ex) {
                closeQuietly(geoReader);
                throw ex;
            }
        }
        return new MmdbIpMetadataProvider(geoReader, asnReader, mode, geoInfo, asnInfo);
    }

    public MmdbDatabaseInfo geoDatabaseInfo() {
        return geoInfo;
    }

    /** ASN database info, or {@code null} when ASN was not configured. */
    public MmdbDatabaseInfo asnDatabaseInfo() {
        return asnInfo;
    }

    public boolean hasAsnDatabase() {
        return asnReader != null;
    }

    @Override
    public IpMetadata lookup(String ip) {
        InetAddress address = IpLiterals.parseLiteralOrNull(ip);
        if (address == null) {
            return null;
        }
        IpAddressScope scope = IpAddressClassifier.scopeOf(address);
        if (scope == IpAddressScope.SPECIAL) {
            return null;
        }
        String canonical = address.getHostAddress();
        try {
            GeoFields geo = lookupGeo(address);
            AsnFields asn = lookupAsn(address);
            if (geo == null && asn == null) {
                return IpMetadata.unknown(canonical, scope);
            }
            return new IpMetadata(
                    canonical,
                    scope,
                    geo == null ? null : geo.countryIso(),
                    geo == null ? null : geo.subdivision(),
                    geo == null ? null : geo.city(),
                    geo == null ? null : geo.latitude(),
                    geo == null ? null : geo.longitude(),
                    geo == null ? null : geo.accuracyRadiusKm(),
                    asn == null ? null : asn.asn(),
                    asn == null ? null : asn.organization(),
                    IpMetadataSource.MMDB,
                    geoInfo.buildEpochSeconds());
        } catch (IOException | GeoIp2Exception ex) {
            // Local read errors should not invent data; treat as unknown for this IP.
            return IpMetadata.unknown(canonical, scope);
        }
    }

    private GeoFields lookupGeo(InetAddress address) throws IOException, GeoIp2Exception {
        if (geoMode == GeoMode.CITY) {
            Optional<CityResponse> city = geoReader.tryCity(address);
            if (city.isEmpty()) {
                return null;
            }
            return GeoFields.fromCity(city.get());
        }
        Optional<CountryResponse> country = geoReader.tryCountry(address);
        if (country.isEmpty()) {
            return null;
        }
        return GeoFields.fromCountry(country.get());
    }

    private AsnFields lookupAsn(InetAddress address) throws IOException, GeoIp2Exception {
        if (asnReader == null) {
            return null;
        }
        Optional<AsnResponse> asn = asnReader.tryAsn(address);
        if (asn.isEmpty()) {
            return null;
        }
        AsnResponse response = asn.get();
        Long number = response.autonomousSystemNumber();
        if (number == null) {
            return null;
        }
        return new AsnFields(number.intValue(), response.autonomousSystemOrganization());
    }

    @Override
    public void close() throws IOException {
        try {
            geoReader.close();
        } finally {
            if (asnReader != null) {
                asnReader.close();
            }
        }
    }

    private static DatabaseReader openReader(Path path) {
        try {
            return new DatabaseReader.Builder(path.toFile()).build();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open MMDB: " + path, ex);
        }
    }

    private static MmdbDatabaseInfo infoOf(DatabaseReader reader) {
        var metadata = reader.metadata();
        long epoch = metadata.buildEpoch().longValueExact();
        return new MmdbDatabaseInfo(metadata.databaseType(), epoch);
    }

    private static void closeQuietly(DatabaseReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // best-effort cleanup during failed open
        }
    }

    private enum GeoMode {
        CITY,
        COUNTRY;

        static GeoMode fromType(String databaseType) {
            String lower = databaseType.toLowerCase();
            if (lower.contains("city") || lower.contains("enterprise")) {
                return CITY;
            }
            if (lower.contains("country")) {
                return COUNTRY;
            }
            throw new IllegalArgumentException("Unsupported GeoIP MMDB type (need City or Country): " + databaseType);
        }
    }

    private record GeoFields(
            String countryIso,
            String subdivision,
            String city,
            Double latitude,
            Double longitude,
            Double accuracyRadiusKm) {
        static GeoFields fromCity(CityResponse response) {
            Country country = response.country();
            String iso = country == null ? null : country.isoCode();
            Subdivision subdivision = response.mostSpecificSubdivision();
            String subdivName = subdivision == null ? null : subdivision.name();
            City city = response.city();
            String cityName = city == null ? null : city.name();
            Location location = response.location();
            Double lat = location == null ? null : location.latitude();
            Double lon = location == null ? null : location.longitude();
            Integer accuracy = location == null ? null : location.accuracyRadius();
            Double accuracyKm = accuracy == null ? null : accuracy.doubleValue();
            if (iso == null
                    && subdivName == null
                    && cityName == null
                    && lat == null
                    && lon == null
                    && accuracyKm == null) {
                return null;
            }
            if ((lat == null) != (lon == null)) {
                lat = null;
                lon = null;
                accuracyKm = null;
            }
            return new GeoFields(iso, subdivName, cityName, lat, lon, accuracyKm);
        }

        static GeoFields fromCountry(CountryResponse response) {
            Country country = response.country();
            String iso = country == null ? null : country.isoCode();
            if (iso == null) {
                return null;
            }
            return new GeoFields(iso, null, null, null, null, null);
        }
    }

    private record AsnFields(int asn, String organization) {}
}
