package com.maxieventos.gateway.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/** Escalares del schema: Date (LocalDate), DateTime (OffsetDateTime), BigDecimal y Long (graphql-java-extended-scalars). */
@Configuration
public class GraphQlConfig {

    @Bean
    RuntimeWiringConfigurer escalares() {
        return wiring -> wiring
                .scalar(ExtendedScalars.Date)
                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLLong);
    }
}
