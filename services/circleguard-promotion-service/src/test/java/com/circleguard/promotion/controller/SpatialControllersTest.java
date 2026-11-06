package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.AccessPointService;
import com.circleguard.promotion.service.BuildingService;
import com.circleguard.promotion.service.FloorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BuildingController.class, FloorController.class, AccessPointController.class})
@Import(SecurityConfig.class)
class SpatialControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuildingService buildingService;
    @MockBean
    private FloorService floorService;
    @MockBean
    private AccessPointService accessPointService;

    private final UUID buildingId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private final UUID apId = UUID.randomUUID();
    private final Building building = Building.builder().id(buildingId).name("Edificio L").code("L")
            .latitude(3.34).longitude(-76.53).address("Calle 18").build();
    private final Floor floor = Floor.builder().id(floorId).building(building).floorNumber(2).name("Piso 2")
            .floorPlanUrl("plano.png").build();
    private final AccessPoint ap = AccessPoint.builder().id(apId).floor(floor).macAddress("AA:BB")
            .coordinateX(1.0).coordinateY(2.0).name("AP-1").build();

    // ---------------------------------------------------------------- edificios

    @Test
    void anyoneCanListBuildingsWithTheirFloors() throws Exception {
        building.setFloors(List.of(floor));
        when(buildingService.getAllBuildings()).thenReturn(List.of(building));

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("L"))
                .andExpect(jsonPath("$[0].floors[0].buildingId").value(buildingId.toString()));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminCreatesAndUpdatesBuildings() throws Exception {
        when(buildingService.createBuilding("Edificio L", "L", null, 3.34, -76.53, "Calle 18")).thenReturn(building);
        when(buildingService.updateBuilding(eq(buildingId), any(), any(), any(), any(), any(), any())).thenReturn(building);
        String body = "{\"name\":\"Edificio L\",\"code\":\"L\",\"latitude\":3.34,\"longitude\":-76.53,\"address\":\"Calle 18\"}";

        mockMvc.perform(post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(buildingId.toString()))
                .andExpect(jsonPath("$.address").value("Calle 18"));
        mockMvc.perform(put("/api/v1/buildings/{id}", buildingId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/buildings/{id}", buildingId))
                .andExpect(status().isOk());

        verify(buildingService).deleteBuilding(buildingId);
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void nonAdminCannotCreateBuildings() throws Exception {
        mockMvc.perform(post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(buildingService);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void floorsOfABuildingAreListedAndAdded() throws Exception {
        when(floorService.getFloorsByBuilding(buildingId)).thenReturn(List.of(floor));
        when(floorService.addFloor(buildingId, 2, "Piso 2")).thenReturn(floor);

        mockMvc.perform(get("/api/v1/buildings/{id}/floors", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floorPlanUrl").value("plano.png"));
        mockMvc.perform(post("/api/v1/buildings/{id}/floors", buildingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":2,\"name\":\"Piso 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorNumber").value(2));
    }

    // -------------------------------------------------------------------- pisos

    @Test
    @WithMockUser(authorities = "ADMIN")
    void floorAccessPointsAndUpdates() throws Exception {
        when(accessPointService.getAccessPointsByFloor(floorId)).thenReturn(List.of(ap));
        when(accessPointService.registerAccessPoint(floorId, "AA:BB", 1.0, 2.0, "AP-1")).thenReturn(ap);
        when(floorService.updateFloor(floorId, 3, "Piso 3", "nuevo.png")).thenReturn(floor);

        mockMvc.perform(get("/api/v1/floors/{id}/access-points", floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floorId").value(floorId.toString()));
        mockMvc.perform(post("/api/v1/floors/{id}/access-points", floorId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB\",\"coordinateX\":1.0,\"coordinateY\":2.0,\"name\":\"AP-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("AA:BB"));
        mockMvc.perform(put("/api/v1/floors/{id}", floorId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":3,\"name\":\"Piso 3\",\"floorPlanUrl\":\"nuevo.png\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/floors/{id}", floorId))
                .andExpect(status().isOk());

        verify(floorService).deleteFloor(floorId);
    }

    // -------------------------------------------------------- puntos de acceso

    @Test
    @WithMockUser(authorities = "ADMIN")
    void accessPointIsReadUpdatedAndDeleted() throws Exception {
        when(accessPointService.getAccessPoint(apId)).thenReturn(Optional.of(ap));
        when(accessPointService.updateAccessPoint(apId, "CC:DD", 5.0, 6.0, "AP-2")).thenReturn(ap);

        mockMvc.perform(get("/api/v1/access-points/{id}", apId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP-1"));
        mockMvc.perform(put("/api/v1/access-points/{id}", apId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"CC:DD\",\"coordinateX\":5.0,\"coordinateY\":6.0,\"name\":\"AP-2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/access-points/{id}", apId))
                .andExpect(status().isOk());

        verify(accessPointService).deleteAccessPoint(apId);
    }

    @Test
    void unknownAccessPointIs404() throws Exception {
        when(accessPointService.getAccessPoint(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/access-points/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
