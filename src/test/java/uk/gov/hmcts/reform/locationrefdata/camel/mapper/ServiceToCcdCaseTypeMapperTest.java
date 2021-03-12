package uk.gov.hmcts.reform.locationrefdata.camel.mapper;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.locationrefdata.camel.binder.ServiceToCcdCaseType;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ServiceToCcdCaseTypeMapperTest {

    ServiceToCcdCaseTypeMapper serviceToCcdCaseTypeMapper = spy(new ServiceToCcdCaseTypeMapper());

    @Test
    public void testGetMap() {
        ServiceToCcdCaseType serviceToCcdCaseType = ServiceToCcdCaseType.builder().serviceCode("test")
            .ccdCaseType(" case1,case2 ").ccdServiceName(" service1 ").build();
        Map<String, Object> resultMap = serviceToCcdCaseTypeMapper.getMap(serviceToCcdCaseType);
        assertEquals(resultMap, ImmutableMap.of(
            "ccd_case_type", "case1,case2",
            "ccd_service_name", "service1",
            "service_code", "test"
        ));
        verify(serviceToCcdCaseTypeMapper, times(1)).getMap(serviceToCcdCaseType);
    }
}
