package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DevAuthDisabledIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void devAuthRoutesDoNotExistWithoutDevAuthProfile() throws Exception {
        mvc.perform(get("/dev-auth/fixtures").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
