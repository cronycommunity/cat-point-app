package com.udacity.catpoint.securityservice.service;

import com.udacity.catpoint.imageservice.service.ImageService;
import com.udacity.catpoint.securityservice.application.StatusListener;
import com.udacity.catpoint.securityservice.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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

    @Mock
    private StatusListener statusListener;

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
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertTrue(securityService.getSensors().stream().allMatch(sensor -> Boolean.FALSE.equals(sensor.getActive())));
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

    @Test
    public void updateSensorWhenArmed() {
        ArmingStatus armingStatus = ArmingStatus.ARMED_HOME;
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        sensor.setActive(true);
        Mockito.when(securityRepository.getSensors())
                .thenReturn(Collections.singleton(sensor));
        securityService.setArmingStatus(armingStatus);
        Mockito.verify(securityRepository, Mockito.times(1)).updateSensor(any());
    }

    @ParameterizedTest
    @EnumSource(ArmingStatus.class)
    public void setArmingStatusMethod(ArmingStatus status) {
        securityService.setArmingStatus(status);
    }

    @Test
    public void testAddAndRemoveStatusListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    public void addAndRemoveSensor() {
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        securityService.addSensor(sensor);
        assertNotNull(securityService.getSensors());
        securityService.removeSensor(sensor);
    }

    @ParameterizedTest
    @CsvSource({"NO_ALARM,DOOR,true", "NO_ALARM,DOOR,false", "PENDING_ALARM,DOOR,true", "PENDING_ALARM,DOOR,false",
            "PENDING_ALARM,WINDOW,true", "PENDING_ALARM,WINDOW,false"})
    public void changeSensorActivationStatusWithAllAlarms(AlarmStatus alarmStatus, SensorType sensorType,
                                                          Boolean active) {
        Mockito.when(securityRepository.getAlarmStatus())
                .thenReturn(AlarmStatus.PENDING_ALARM);
        Sensor sensor = new Sensor("test", sensorType);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, active);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, active);
        Mockito.when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        sensor = new Sensor("test", sensorType);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, active);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, active);
    }

}
