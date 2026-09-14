package com.irs.mef.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.inspector.capture.ReflectiveMimeTap;
import com.irs.mef.inspector.capture.RingSendSubmissionsWireTap;
import com.irs.mef.inspector.ring.EmptySendSnapshotRing;
import com.irs.mef.inspector.ring.FileSendSnapshotRing;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.view.InspectorHtml;
import com.irs.mef.newsend.gateway.NoOpSendSubmissionsWireTap;
import com.irs.mef.newsend.gateway.SendSubmissionsWireTap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class InspectorConfig {

    @Bean
    public SendSnapshotRing sendSnapshotRing(InspectorProperties properties, ObjectMapper objectMapper) {
        if (!properties.isEnabled()) {
            return new EmptySendSnapshotRing();
        }
        return new FileSendSnapshotRing(
                properties.resolvedPath(), properties.ringSizeClamped(), objectMapper);
    }

    @Bean
    public InspectorHtml inspectorHtml(InspectorProperties properties) {
        return new InspectorHtml(properties.ringSizeClamped());
    }

    @Bean
    public ReflectiveMimeTap reflectiveMimeTap(InspectorProperties properties) {
        return new ReflectiveMimeTap(properties.maxXmlBytesOrDefault());
    }

    @Bean
    public SendSubmissionsWireTap sendSubmissionsWireTap(InspectorProperties properties,
                                                         SendSnapshotRing sendSnapshotRing,
                                                         ReflectiveMimeTap reflectiveMimeTap,
                                                         Clock clock,
                                                         MefSdkConfig mefSdkConfig) {
        if (!properties.isEnabled()) {
            return NoOpSendSubmissionsWireTap.INSTANCE;
        }
        String environment = mefSdkConfig.getEnvironment() == null ? "ATS" : mefSdkConfig.getEnvironment();
        return new RingSendSubmissionsWireTap(
                sendSnapshotRing,
                reflectiveMimeTap,
                clock,
                environment,
                properties.maxXmlBytesOrDefault());
    }
}
