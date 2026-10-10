package com.oula.property;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Declared private property inventory. No intake creates a VERIFIED fact. */
@Service
public class PropertyIntakeService {
    private final JdbcClient jdbc;
    public PropertyIntakeService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public PropertyView createProperty(AccessContext access, UUID id, String type,
                                       String district, int beds, BigDecimal price, boolean demo) {
        check(access);
        if (!"RESIDENTIAL".equals(type) && !"COMMERCIAL".equals(type))
            throw new IllegalArgumentException("invalid asset type");
        if (district == null || district.isBlank() || district.length() > 160 ||
            beds < 0 || beds > 100 || price == null || price.signum() <= 0)
            throw new IllegalArgumentException("invalid property attributes");
        jdbc.sql("""
            insert into property.asset (id,workspace_id,asset_type,district,bedrooms,asking_price)
            values (:id,:ws,:type,:district,:beds,:price)
            """).param("id",id).param("ws",access.workspaceId()).param("type",type)
            .param("district",district).param("beds",beds).param("price",price).update();
        String source = demo ? "DEMO_FIXTURE" : "USER_DECLARATION";
        fact(id,"intake_origin",demo ? "DEMO" : "USER_DECLARED",source);
        fact(id,"district",district,source);
        fact(id,"bedrooms",Integer.toString(beds),source);
        fact(id,"asking_price",price.toPlainString(),source);
        return getProperty(access,id);
    }

    @Transactional(readOnly = true)
    public PropertyView getProperty(AccessContext access, UUID id) {
        check(access);
        return jdbc.sql("""
            select p.id,p.workspace_id,p.asset_type,p.district,p.bedrooms,p.asking_price,
                   (select f.source_type from property.fact f where f.property_id=p.id
                    and f.fact_key='intake_origin' order by f.created_at desc limit 1) source_type
              from property.asset p where p.id=:id and p.workspace_id=:ws
            """).param("id",id).param("ws",access.workspaceId())
            .query((rs,n)->new PropertyView(rs.getObject("id",UUID.class),
                rs.getObject("workspace_id",UUID.class),rs.getString("asset_type"),
                rs.getString("district"),(Integer)rs.getObject("bedrooms"),
                rs.getBigDecimal("asking_price"),origin(rs.getString("source_type")),
                source(rs.getString("source_type")),truth(rs.getString("source_type"))))
            .optional().orElseThrow(()->new NoSuchElementException("property not found"));
    }

    @Transactional
    public ListingView createListing(AccessContext access, UUID id, UUID propertyId,
                                     String type, BigDecimal price) {
        check(access);
        if (!"SALE".equals(type) && !"RENT".equals(type))
            throw new IllegalArgumentException("invalid transaction type");
        if (price == null || price.signum() <= 0)
            throw new IllegalArgumentException("invalid asking price");
        getProperty(access,propertyId);
        jdbc.sql("""
            insert into market.listing
              (id,workspace_id,property_id,transaction_type,status,asking_price,currency)
            values (:id,:ws,:property,:type,'DRAFT',:price,'SAR')
            """).param("id",id).param("ws",access.workspaceId())
            .param("property",propertyId).param("type",type)
            .param("price",price).update();
        return getListing(access,id);
    }

    @Transactional(readOnly = true)
    public ListingView getListing(AccessContext access, UUID id) {
        check(access);
        return jdbc.sql(listingSql()+" where l.id=:id and l.workspace_id=:ws")
            .param("id",id).param("ws",access.workspaceId())
            .query((rs,n)->mapListing(rs)).optional()
            .orElseThrow(()->new NoSuchElementException("listing not found"));
    }

    @Transactional(readOnly = true)
    public List<ListingView> searchListings(AccessContext access, String district) {
        check(access);
        if (district != null && district.length() > 160)
            throw new IllegalArgumentException("district exceeds 160 characters");
        return jdbc.sql(listingSql()+"""
             where l.workspace_id=:ws
               and (cast(:district as varchar) is null or p.district=:district)
             order by p.district,l.id
            """).param("ws",access.workspaceId()).param("district",district)
            .query((rs,n)->mapListing(rs)).list();
    }

    private String listingSql() {
        return """
            select l.id,l.workspace_id,l.property_id,l.transaction_type,l.status,
                   l.asking_price,l.currency,
                   (select f.source_type from property.fact f where f.property_id=p.id
                    and f.fact_key='intake_origin' order by f.created_at desc limit 1) source_type
              from market.listing l join property.asset p on p.id=l.property_id
                   and p.workspace_id=l.workspace_id
            """;
    }

    private ListingView mapListing(java.sql.ResultSet rs) throws java.sql.SQLException {
        String s=rs.getString("source_type");
        return new ListingView(rs.getObject("id",UUID.class),
            rs.getObject("workspace_id",UUID.class),rs.getObject("property_id",UUID.class),
            rs.getString("transaction_type"),rs.getString("status"),
            rs.getBigDecimal("asking_price"),rs.getString("currency"),
            origin(s),source(s),truth(s));
    }

    private void fact(UUID propertyId,String key,String value,String source) {
        jdbc.sql("""
            insert into property.fact (id,property_id,fact_key,value_json,truth_status,source_type)
            values (:id,:property,:key,jsonb_build_object('value',:value),'DECLARED',:source)
            """).param("id",UUID.randomUUID()).param("property",propertyId)
            .param("key",key).param("value",value).param("source",source).update();
    }

    private static void check(AccessContext a) {
        if (a == null || a.purpose()!=AccessPurpose.PROPERTY_DECISION_SUPPORT)
            throw new SecurityException("decision support purpose required");
    }
    private static String source(String x) { return x == null ? "UNKNOWN" : x; }
    private static String truth(String x) { return x == null ? "UNKNOWN" : "DECLARED"; }
    private static String origin(String x) {
        return "DEMO_FIXTURE".equals(x) ? "DEMO" :
               "USER_DECLARATION".equals(x) ? "USER_DECLARED" : "UNKNOWN";
    }

    public record PropertyView(UUID id,UUID workspaceId,String assetType,String district,
                               Integer bedrooms,BigDecimal askingPrice,String dataOrigin,
                               String sourceType,String truthStatus) {}
    public record ListingView(UUID id,UUID workspaceId,UUID propertyId,
                              String transactionType,String status,BigDecimal askingPrice,
                              String currency,String dataOrigin,String sourceType,
                              String truthStatus) {}
}
