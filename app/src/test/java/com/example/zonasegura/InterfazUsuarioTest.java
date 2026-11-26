package com.example.zonasegura;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class InterfazUsuarioTest {
    @Test public void allFifteenCombinationsShowExactlyTheirAssignedRoles(){
        var all=InterfazUsuario.values();
        for(int mask=1;mask<16;mask++){
            List<String> roles=new ArrayList<>();
            for(int bit=0;bit<all.length;bit++)if((mask&(1<<bit))!=0)roles.add(all[bit].rol);
            var choices=InterfazUsuario.disponibles(roles);
            assertEquals(Integer.bitCount(mask),choices.size());
            for(var role:all)assertEquals(roles.contains(role.rol),choices.contains(role));
        }
    }
    @Test public void citizenAndMunicipalKeepBothChoices(){
        assertEquals(List.of(InterfazUsuario.CIUDADANO,InterfazUsuario.ALCALDIA),InterfazUsuario.disponibles(List.of("CIUDADANO","PERSONAL_ALCALDIA")));
    }
    @Test public void duplicatesAndUnknownRolesCannotCreateExtraInterfaces(){
        assertEquals(List.of(InterfazUsuario.CIUDADANO),InterfazUsuario.disponibles(List.of("CIUDADANO","SUPERADMIN","CIUDADANO")));
        assertTrue(InterfazUsuario.disponibles(List.of()).isEmpty());
        assertTrue(InterfazUsuario.disponibles(null).isEmpty());
        assertNull(InterfazUsuario.buscar("SUPERADMIN"));
    }
}
