package io.github.fabiantorrestech.touchwallpaper.font;

import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks a band's text color by changing the fewest sampled pixels needed, spread evenly.
 * Only the red channel changes. A panel test showed the changes can't be seen. The output must
 * stay lossless, because any re-encoding would undo this.
 */
public final class TextColorForcer {
    /** How far past a tie to push the vote, so the result isn't balanced on one pixel. */
    private static final int VOTE_MARGIN = 200;
    private static final int RED_MASK_CLEARED = 0xFF00FFFF;

    private TextColorForcer() {
    }

    /** Returns the number of pixels changed. Zero means the band already votes for the target. */
    public static int apply(int[] pixels, int width, Band band, TextColor target) {
        boolean wantWhiteVote = target == TextColor.BLACK;
        int vote = FontColorRule.vote(pixels, width, band);
        int signedVote = wantWhiteVote ? vote : -vote;
        if (signedVote >= VOTE_MARGIN) {
            return 0;
        }
        // Flipping one sample moves the vote by 2.
        int needed = (VOTE_MARGIN - signedVote + 1) / 2;
        List<Integer> candidates = samplesVotingAgainst(pixels, width, band, wantWhiteVote);
        int flips = Math.min(needed, candidates.size());
        int newRed = wantWhiteVote ? FontColorRule.RED_THRESHOLD + 1 : FontColorRule.RED_THRESHOLD;
        for (int i = 0; i < flips; i++) {
            int index = candidates.get((int) ((long) i * candidates.size() / flips));
            pixels[index] = (pixels[index] & RED_MASK_CLEARED) | (newRed << 16);
        }
        return flips;
    }

    private static List<Integer> samplesVotingAgainst(int[] pixels, int width, Band band, boolean wantWhiteVote) {
        List<Integer> indexes = new ArrayList<>();
        for (int y = FontColorRule.firstSampleRow(band); y < band.bottom; y += FontColorRule.SAMPLE_STEP) {
            for (int x = FontColorRule.SAMPLE_STEP; x < width; x += FontColorRule.SAMPLE_STEP) {
                int index = y * width + x;
                if (FontColorRule.votesWhite(pixels[index]) != wantWhiteVote) {
                    indexes.add(index);
                }
            }
        }
        return indexes;
    }
}
