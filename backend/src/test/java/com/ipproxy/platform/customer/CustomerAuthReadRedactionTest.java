package com.ipproxy.platform.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.customer.mapper.CustomerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Material privacy must be enforced by API service projection, not Vue display masking. */
class CustomerAuthReadRedactionTest {
    private static Map<String,Object> raw(){
        Map<String,Object> record=new LinkedHashMap<>();
        record.put("id",47L);
        record.put("auth_no","CI-KYC-47");
        record.put("customer_id",7L);
        record.put("customer_name","CI Owner");
        record.put("status","PENDING");
        record.put("submitted_data","{\"subjectName\":\"CI Owner\",\"documentNo\":\"ABC987654321\"}");
        record.put("attachment_refs","[\"private://document-1\",\"private://document-2\"]");
        return record;
    }
    private static CustomerService service(CustomerMapper mapper){
        return new CustomerService(mapper,mock(PasswordEncoder.class),new ObjectMapper());
    }
    @Test void individualSummaryNeverReturnsOriginalIdentityDocumentOrPrivateAttachmentRefs(){
        CustomerMapper mapper=mock(CustomerMapper.class);
        when(mapper.getAuth(47L)).thenReturn(raw());
        Map<String,Object> result=service(mapper).auth(47L);
        assertEquals(47L,result.get("id"));
        assertEquals("CI-KYC-47",result.get("auth_no"));
        assertEquals("CI Owner",result.get("subject_name"));
        assertEquals("ABC••••4321",result.get("document_number_masked"));
        assertEquals(2,result.get("attachment_count"));
        assertFalse(result.containsKey("submitted_data"));
        assertFalse(result.containsKey("attachment_refs"));
        assertFalse(result.toString().contains("ABC987654321"));
        assertFalse(result.toString().contains("private://"));
    }
    @Test void listAndCustomerSubtableCannotLeakRawDocumentsEvenIfMapperReturnsThem(){
        CustomerMapper mapper=mock(CustomerMapper.class);
        when(mapper.countAuths(anyLong(),anyString(),anyString())).thenReturn(1L);
        when(mapper.listAuths(anyLong(),anyString(),anyString(),anyInt(),anyInt())).thenReturn(List.of(raw()));
        when(mapper.getCustomer(7L)).thenReturn(Map.of("id",7L,"customer_name","CI Owner"));
        var instance=service(mapper);
        var page=instance.auths(1,20,null,"","");
        assertEquals(1,page.items().size());
        assertFalse(page.items().get(0).containsKey("submitted_data"));
        assertFalse(page.items().get(0).containsKey("attachment_refs"));
        Map<String,Object> detail=instance.detail(7L);
        @SuppressWarnings("unchecked")
        var subtable=(List<Map<String,Object>>)detail.get("authRecords");
        assertEquals(1,subtable.size());
        assertFalse(subtable.get(0).containsKey("submitted_data"));
        assertFalse(subtable.get(0).containsKey("attachment_refs"));
        assertFalse(detail.toString().contains("ABC987654321"));
        assertFalse(detail.toString().contains("private://"));
    }
}
