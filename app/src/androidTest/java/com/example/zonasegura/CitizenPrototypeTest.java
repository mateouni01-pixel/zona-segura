package com.example.zonasegura;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.zonasegura.citizen.CitizenActivity;
import com.example.zonasegura.citizen.CitizenReport;
import com.example.zonasegura.citizen.DemoReportRepository;
import com.example.zonasegura.citizen.ReportRules;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CitizenPrototypeTest {
    private Context context;
    @Before public void resetDemoOnly() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new DemoReportRepository(context).resetDemo();
    }

    @Test public void formShowsErrorsAndDoesNotCreateInvalidReport() {
        try (ActivityScenario<CitizenActivity> scenario = ActivityScenario.launch(CitizenActivity.class)) {
            onView(withId(R.id.navCitizenReport)).perform(click());
            onView(withId(R.id.formSave)).perform(scrollTo(), click());
            onView(withText(R.string.zs_type_required)).check(matches(isDisplayed()));
            assertEquals(4, new DemoReportRepository(context).all().size());
        }
    }

    @Test public void reportCanBeCreatedAndReadFromConfirmation() {
        try (ActivityScenario<CitizenActivity> scenario = ActivityScenario.launch(CitizenActivity.class)) {
            onView(withId(R.id.navCitizenReport)).perform(click());
            onView(withId(R.id.formType)).perform(scrollTo(), click());
            onView(withText(R.string.zs_lighting)).inRoot(isPlatformPopup()).perform(click());
            onView(withId(R.id.formDescription)).perform(replaceText("Prueba de una calle sin alumbrado."), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.formDescription)).check(matches(withText("Prueba de una calle sin alumbrado.")));
            onView(withId(R.id.formSave)).perform(scrollTo(), click());
            onView(withId(R.id.resultTitle)).check(matches(withText(R.string.zs_confirm_title)));
            onView(withId(R.id.resultOpen)).perform(scrollTo(), click());
            onView(withId(R.id.detailDescription)).check(matches(withText("Prueba de una calle sin alumbrado.")));
            assertEquals(5, new DemoReportRepository(context).all().size());
        }
    }

    @Test public void mineFilterHasAnActionableEmptyState() {
        try (ActivityScenario<CitizenActivity> scenario = ActivityScenario.launch(CitizenActivity.class)) {
            onView(withId(R.id.navCitizenValidate)).perform(click());
            onView(withId(R.id.reportFilterMine)).perform(click());
            onView(withId(R.id.citizenReportsEmpty)).check(matches(isDisplayed()));
            onView(withId(R.id.citizenEmptyCreate)).perform(click());
            onView(withId(R.id.formType)).check(matches(isDisplayed()));
        }
    }

    @Test public void negativeVotePersistsAndCannotBeCountedTwice() {
        DemoReportRepository repository = new DemoReportRepository(context);
        assertEquals(ReportRules.VoteBlock.NONE, repository.vote(1042, false));
        CitizenReport voted = new DemoReportRepository(context).find(1042);
        assertNotNull(voted);
        assertEquals(-1, voted.myVote);
        assertEquals(4, voted.negativeVotes);
        assertEquals(ReportRules.VoteBlock.ALREADY_VOTED, repository.vote(1042, true));
        assertEquals(6, repository.find(1042).voteCount());
    }
}
