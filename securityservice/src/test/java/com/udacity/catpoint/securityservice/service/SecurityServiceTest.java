package com.udacity.catpoint.securityservice.service;

import com.udacity.catpoint.imageservice.service.ImageService;
import com.udacity.catpoint.securityservice.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private Sensor sensor;

    private final String randomString = UUID.randomUUID().toString();

    @Mock
    SecurityRepository securityRepository;

    @Mock
    ImageService imageService;

    private
    SecurityService securityService;

    private Sensor getNewSensor() {
        return new Sensor(randomString, SensorType.DOOR);
    }

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = getNewSensor();
    }

    private Set<Sensor> getSensors(boolean active, int count) {
        String randomString = UUID.randomUUID().toString();

        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i <= count; i++) {
            sensors.add(new Sensor(randomString, SensorType.DOOR));
        }
        sensors.forEach(it -> it.setActive(active));
        return sensors;
    }

    //1
    @Test
    void putStatusToPending_IfSystemArmedAndSensorActivated() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    //2
    @Test
    void setStatusToAlarm_IfSystemArmedAndSensorActivatedAndPendingState() {
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMost(2)).setAlarmStatus(AlarmStatus.ALARM); //first call up
    }

    //3
    @Test
    public void pendingAlarmWithInactiveSensorsNoAlarmResult() {
        Sensor sensor = new Sensor("mysensor", SensorType.DOOR);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        sensor.setActive(false);
        Mockito.when(securityRepository.getAlarmStatus())
                .thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        Mockito.verify(securityRepository, Mockito.times(1))
                .setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //4
    @Test
    void changeInAlarmStateNotAffectedAlarmState_IfAlarmIsActive() {
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(securityRepository, atMostOnce()).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMost(2)).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);
    }

    //5
    @Test
    public void changeStatusToAlarm_IfSensorActivatedWhileActiveAndPendingAlarm() {
        Sensor sensor = new Sensor("mytestsensor", SensorType.DOOR);
        Mockito.when(securityRepository.getSensors()).thenReturn(getSensors(true, 2));
        Mockito.when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        Mockito.when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        Mockito.verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

    //6
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void changeAlarmState_sensorDeactivateWhileInactive_noChangeToAlarmState(AlarmStatus alarmStatus) {
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    //7
    @Test
    void putSystemIntoAlarm_IfImageContainingCatDetected() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

    //8
    @Test
    public void ChangeToStatusNoAlarm_IfCatNotDetected() throws BackingStoreException {
        Mockito.when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat()))
                .thenReturn(Boolean.FALSE);
        BufferedImage bufferedImage = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        sensor.setActive(false);
        securityService.processImage(bufferedImage);
        Mockito.verify(securityRepository, Mockito.times(1))
                .setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //9
    @Test
    void setNoAlarmStatus_IfSystemDisarmed() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //10
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void resetAllSensors_IfTheSystemIsArmed(ArmingStatus armingStatus) {
        Set<Sensor> sensors = getSensors(true, 4);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        List<Executable> executables = new ArrayList<>();
        sensors.forEach(it -> executables.add(() -> assertEquals(it.getActive(), false)));
        assertAll(executables);
    }

    //11
    @Test
    void setAlarmStatusToAlarm_systemArmedHomeAndCatDetected() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

}
