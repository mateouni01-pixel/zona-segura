package com.example.zonasegura.citizen;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReportRulesTest {
    @Test public void descriptionRequiresMeaningfulLengthAfterTrimming() {
        assertFalse(ReportRules.validDescription(null));
        assertFalse(ReportRules.validDescription("          "));
        assertFalse(ReportRules.validDescription("123456789"));
        assertTrue(ReportRules.validDescription("  1234567890  "));
        assertFalse(ReportRules.validDescription(new String(new char[2001]).replace('\0', 'a')));
    }
    @Test public void coordinatesAllowZeroAndRejectNonFiniteOrOutOfRange() {
        assertTrue(ReportRules.validCoordinates(0, 0));
        assertTrue(ReportRules.validCoordinates(-90, 180));
        assertFalse(ReportRules.validCoordinates(91, 0));
        assertFalse(ReportRules.validCoordinates(0, -181));
        assertFalse(ReportRules.validCoordinates(Double.NaN, 0));
        assertFalse(ReportRules.validCoordinates(0, Double.POSITIVE_INFINITY));
    }
    @Test public void voteDistinguishesDuplicateFarOwnAndClosedReports() {
        CitizenReport report = new CitizenReport();
        report.latitude = -17.783300; report.longitude = -63.182100;
        assertEquals(ReportRules.VoteBlock.NONE, ReportRules.voteBlock(report));
        report.latitude = -17.790000;
        assertEquals(ReportRules.VoteBlock.TOO_FAR, ReportRules.voteBlock(report));
        report.latitude = -17.783300; report.myVote = -1;
        assertEquals(ReportRules.VoteBlock.ALREADY_VOTED, ReportRules.voteBlock(report));
        report.myVote = 0; report.mine = true;
        assertEquals(ReportRules.VoteBlock.OWN_REPORT, ReportRules.voteBlock(report));
        report.mine = false; report.state = "VALIDADO";
        assertEquals(ReportRules.VoteBlock.CLOSED, ReportRules.voteBlock(report));
    }
    @Test public void negativeVoteLowersConfirmationRatio() {
        CitizenReport report = new CitizenReport();
        report.positiveVotes = 2; report.negativeVotes = 3;
        assertEquals(40, report.confirmationPercent());
        report.negativeVotes++;
        assertEquals(33, report.confirmationPercent());
    }

    @Test public void radiusIncludes500MetersButRejects500PointOne() {
        CitizenReport report = new CitizenReport(); report.longitude = -63.182100;
        report.latitude = -17.783300 + Math.toDegrees(500.0 / 6371000);
        assertTrue(ReportRules.withinVotingRadius(report));
        report.latitude = -17.783300 + Math.toDegrees(500.1 / 6371000);
        assertFalse(ReportRules.withinVotingRadius(report));
    }
}
