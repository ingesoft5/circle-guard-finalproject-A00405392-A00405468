package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SpatialServicesTest {

    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final FloorRepository floors = mock(FloorRepository.class);
    private final AccessPointRepository accessPoints = mock(AccessPointRepository.class);

    private final SpatialService spatial = new SpatialService(buildings, floors, accessPoints);
    private final BuildingService buildingService = new BuildingService(buildings, floors);
    private final AccessPointService apService = new AccessPointService(accessPoints, floors);

    private final UUID buildingId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private final Building building = Building.builder().id(buildingId).name("Edificio L").code("L").build();
    private final Floor floor = Floor.builder().id(floorId).building(building).floorNumber(2).name("Piso 2").build();

    {
        when(buildings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(floors.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accessPoints.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ edificios

    @Test
    void createsAndUpdatesBuildings() {
        Building created = spatial.createBuilding("Edificio L", "L", "Laboratorios");
        assertThat(created.getCode()).isEqualTo("L");

        Building withMap = buildingService.createBuilding("Biblioteca", "B", "Central", 3.34, -76.53, "Calle 18");
        assertThat(withMap.getLatitude()).isEqualTo(3.34);
        assertThat(withMap.getAddress()).isEqualTo("Calle 18");

        when(buildings.findById(buildingId)).thenReturn(Optional.of(building));
        assertThat(spatial.updateBuilding(buildingId, "Edificio L2", "L2", "Nuevo").getName()).isEqualTo("Edificio L2");
        Building updated = buildingService.updateBuilding(buildingId, "L3", "L3", "d", 1.0, 2.0, "dir");
        assertThat(updated.getLongitude()).isEqualTo(2.0);

        when(buildings.findAll()).thenReturn(List.of(building));
        assertThat(spatial.getAllBuildings()).containsExactly(building);
        assertThat(buildingService.getAllBuildings()).containsExactly(building);
    }

    @Test
    void updatingUnknownBuildingFails() {
        when(buildings.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spatial.updateBuilding(UUID.randomUUID(), "x", "x", "x")).hasMessage("Building not found");
        assertThatThrownBy(() -> buildingService.updateBuilding(UUID.randomUUID(), "x", "x", "x", 0.0, 0.0, "x"))
                .hasMessage("Building not found");
    }

    @Test
    void buildingWithFloorsCannotBeDeleted() {
        when(floors.findByBuildingId(buildingId)).thenReturn(List.of(floor));

        assertThatThrownBy(() -> spatial.deleteBuilding(buildingId)).hasMessageContaining("existing floors");
        assertThatThrownBy(() -> buildingService.deleteBuilding(buildingId)).hasMessageContaining("existing floors");
        verify(buildings, never()).deleteById(any());
    }

    @Test
    void emptyBuildingIsDeleted() {
        when(floors.findByBuildingId(buildingId)).thenReturn(List.of());

        spatial.deleteBuilding(buildingId);
        buildingService.deleteBuilding(buildingId);

        verify(buildings, times(2)).deleteById(buildingId);
    }

    // ---------------------------------------------------------------------- pisos

    @Test
    void floorsAreAddedUpdatedAndListedPerBuilding() {
        when(buildings.findById(buildingId)).thenReturn(Optional.of(building));
        Floor added = spatial.addFloor(buildingId, 3, "Piso 3");
        assertThat(added.getBuilding()).isSameAs(building);

        when(floors.findById(floorId)).thenReturn(Optional.of(floor));
        assertThat(spatial.updateFloor(floorId, 4, "Piso 4").getFloorNumber()).isEqualTo(4);

        when(floors.findByBuildingId(buildingId)).thenReturn(List.of(floor));
        assertThat(spatial.getFloorsByBuilding(buildingId)).containsExactly(floor);
    }

    @Test
    void floorErrorsAreReported() {
        when(buildings.findById(any())).thenReturn(Optional.empty());
        when(floors.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spatial.addFloor(UUID.randomUUID(), 1, "x")).hasMessage("Building not found");
        assertThatThrownBy(() -> spatial.updateFloor(UUID.randomUUID(), 1, "x")).hasMessage("Floor not found");
    }

    @Test
    void floorWithAccessPointsCannotBeDeletedButEmptyOneCan() {
        when(accessPoints.findByFloorId(floorId)).thenReturn(List.of(AccessPoint.builder().build()));
        assertThatThrownBy(() -> spatial.deleteFloor(floorId)).hasMessageContaining("existing access points");

        when(accessPoints.findByFloorId(floorId)).thenReturn(List.of());
        spatial.deleteFloor(floorId);
        verify(floors).deleteById(floorId);
    }

    // --------------------------------------------------------- puntos de acceso

    @Test
    void accessPointsLifecycleInBothServices() {
        when(floors.findById(floorId)).thenReturn(Optional.of(floor));
        AccessPoint fromSpatial = spatial.registerAccessPoint(floorId, "AA:BB", 1.0, 2.0, "AP-1");
        AccessPoint fromService = apService.registerAccessPoint(floorId, "CC:DD", 3.0, 4.0, "AP-2");
        assertThat(fromSpatial.getFloor()).isSameAs(floor);
        assertThat(fromService.getMacAddress()).isEqualTo("CC:DD");

        UUID apId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(apId).macAddress("AA:BB").build();
        when(accessPoints.findById(apId)).thenReturn(Optional.of(ap));
        assertThat(spatial.updateAccessPoint(apId, "EE:FF", 5.0, 6.0, "AP-1b").getMacAddress()).isEqualTo("EE:FF");
        assertThat(apService.updateAccessPoint(apId, "11:22", 7.0, 8.0, "AP-1c").getCoordinateY()).isEqualTo(8.0);
        assertThat(spatial.getAccessPoint(apId)).contains(ap);
        assertThat(apService.getAccessPoint(apId)).contains(ap);

        when(accessPoints.findByFloorId(floorId)).thenReturn(List.of(ap));
        assertThat(spatial.getAccessPointsByFloor(floorId)).containsExactly(ap);
        assertThat(apService.getAccessPointsByFloor(floorId)).containsExactly(ap);

        spatial.deleteAccessPoint(apId);
        apService.deleteAccessPoint(apId);
        verify(accessPoints, times(2)).deleteById(apId);
    }

    @Test
    void accessPointErrorsAreReported() {
        when(floors.findById(any())).thenReturn(Optional.empty());
        when(accessPoints.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spatial.registerAccessPoint(UUID.randomUUID(), "m", 0.0, 0.0, "n")).hasMessage("Floor not found");
        assertThatThrownBy(() -> apService.registerAccessPoint(UUID.randomUUID(), "m", 0.0, 0.0, "n")).hasMessage("Floor not found");
        assertThatThrownBy(() -> spatial.updateAccessPoint(UUID.randomUUID(), "m", 0.0, 0.0, "n")).hasMessage("Access Point not found");
        assertThatThrownBy(() -> apService.updateAccessPoint(UUID.randomUUID(), "m", 0.0, 0.0, "n")).hasMessage("Access Point not found");
    }

    // ------------------------------------------------- resolución de ubicación

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final MacSessionRegistry sessions = mock(MacSessionRegistry.class);
    private final GraphService graph = mock(GraphService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> redisSets = mock(SetOperations.class);
    private final LocationResolutionService location =
            new LocationResolutionService(accessPoints, sessions, graph, kafka, redis);

    @Test
    void knownDeviceNearAccessPointRecordsEncountersWithOthersThere() {
        UUID apId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(apId).macAddress("AP:01").floor(floor)
                .coordinateX(10.0).coordinateY(20.0).name("AP-1").build();
        when(accessPoints.findByMacAddress("AP:01")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("DEV:01")).thenReturn("user-a");
        when(redis.opsForSet()).thenReturn(redisSets);
        when(redisSets.members("spatial:location:" + apId)).thenReturn(Set.of("user-a", "user-b"));

        location.processSignal("AP:01", "DEV:01", -55.0);

        verify(kafka).send(eq("proximity.detected"), eq("user-a"), any());
        verify(graph).recordEncounter("user-a", "user-b", apId.toString());
        verify(graph, never()).recordEncounter(eq("user-a"), eq("user-a"), anyString());
        verify(redisSets).add("spatial:location:" + apId, "user-a");
        verify(graph).detectAndFormCircles(apId.toString());
    }

    @Test
    void unknownAccessPointOrUnmappedDeviceIsIgnored() {
        when(accessPoints.findByMacAddress("AP:??")).thenReturn(Optional.empty());
        location.processSignal("AP:??", "DEV:01", -60.0);

        AccessPoint ap = AccessPoint.builder().id(UUID.randomUUID()).floor(floor).coordinateX(0.0).coordinateY(0.0).build();
        when(accessPoints.findByMacAddress("AP:01")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("RANDOM:MAC")).thenReturn(null);
        location.processSignal("AP:01", "RANDOM:MAC", -60.0);

        verifyNoInteractions(kafka, graph, redis);
    }
}
