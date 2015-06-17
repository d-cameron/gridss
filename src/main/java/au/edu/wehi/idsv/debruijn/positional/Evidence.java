package au.edu.wehi.idsv.debruijn.positional;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import au.edu.wehi.idsv.BreakendDirection;
import au.edu.wehi.idsv.NonReferenceReadPair;
import au.edu.wehi.idsv.SoftClipEvidence;
import au.edu.wehi.idsv.debruijn.KmerEncodingHelper;
import au.edu.wehi.idsv.debruijn.PackedKmerList;
import au.edu.wehi.idsv.sam.SAMRecordUtil;

/**
 * Minimal evidence for incorporating soft clip and
 * read pair evidence into a de Bruijn graph
 * 
 * @author Daniel Cameron
 *
 */
public class Evidence extends PackedKmerList {
	private String id;
	private BitSet anchor;
	private BitSet ambiguous;
	private int start;
	private int end;
	public KmerSupportNode node(int offset) {
		if (ambiguous != null && ambiguous.get(offset)) {
			return null;
		}
		return new KmerSupportNode(this, offset);
	}
	public String evidenceId() { return id; }
	/**
	 * Start position of first kmer
	 * @return
	 */
	public int startPosition() { 
		return start;
	}
	/**
	 * End position of first kmer
	 * @return
	 */
	public int endPosition() { 
		return end;
	}
	public boolean isAnchored(int offset) {
		if (anchor == null) return false;
		return anchor.get(offset);
	}
	private static BitSet ambiguousKmers(int k, byte[] bases) {
		BitSet ambiguous = null;
		for (int i = 0; i < bases.length; i++) {
			if (KmerEncodingHelper.isAmbiguous(bases[i])) {
				int kmerCount = bases.length - k + 1;
				if (ambiguous == null) {
					ambiguous = new BitSet(kmerCount);
				}
				ambiguous.set(Math.max(0, i - k + 1), Math.min(i, kmerCount - 1) + 1, true);
			}
		}
		return ambiguous;
	}
	public Evidence(
			String evidenceId,
			int start,
			int end,
			int k, BitSet anchorKmers, byte[] bases, byte[] qual, boolean reverse, boolean complement) {
		super(k, bases, qual, reverse, complement);
		assert(evidenceId != null);
		assert(qual.length == bases.length);
		this.id = evidenceId;
		this.start = start;
		this.end = end;
		this.ambiguous = ambiguousKmers(k, bases);
		this.anchor = anchorKmers;
	}
	public static Evidence create(int k, NonReferenceReadPair pair) {
		SAMRecord local = pair.getLocalledMappedRead();
		SAMRecord remote = pair.getNonReferenceRead();
		boolean reverseComp = !pair.onExpectedStrand();
		int startPosition;
		int endPosition;
		int maxFragSize = pair.getEvidenceSource().getMaxConcordantFragmentSize();
		int minFragSize = pair.getEvidenceSource().getMinConcordantFragmentSize();
		if (pair.getBreakendSummary().direction == BreakendDirection.Forward) {
			//  ----->          anchored
			//  |-------------| max size
			//           <-----
			//  |-----|         minsize
			//   <----- 
			startPosition = local.getUnclippedStart() + minFragSize - remote.getReadLength();
			endPosition = local.getUnclippedStart() + maxFragSize - remote.getReadLength();
		} else {
			//           <----- anchored
			//  |-------------| max size
			//  ----->
			//          |-----| minsize
			//          -----> 
			startPosition = local.getUnclippedEnd() - maxFragSize + 1;
			endPosition = local.getUnclippedEnd() - minFragSize + 1;
		}
		return new Evidence(pair.getEvidenceID(), startPosition, endPosition, k, null, remote.getReadBases(), remote.getBaseQualities(), reverseComp, reverseComp);
	}
	public static Evidence create(int k, SoftClipEvidence softClipEvidence, boolean trimOtherSoftClip) {
		SAMRecord read = softClipEvidence.getSAMRecord();
		List<CigarElement> elements = read.getCigar().getCigarElements();
		int startClipLength = SAMRecordUtil.getStartSoftClipLength(elements);
		int endClipLength = SAMRecordUtil.getEndSoftClipLength(elements);
		
		if (trimOtherSoftClip && endClipLength > 0 && startClipLength > 0) {
			SAMRecord trimmed;
			try {
				trimmed = (SAMRecord)read.clone();
			} catch (CloneNotSupportedException e) {
				throw new RuntimeException(e);
			}
			List<CigarElement> ce = new ArrayList<CigarElement>(trimmed.getCigar().getCigarElements());
			int trimStart;
			int trimEnd;
			if (softClipEvidence.getBreakendSummary().direction == BreakendDirection.Forward) {
				trimEnd = 0;
				trimStart = startClipLength;
				while (ce.get(0).getOperator() == CigarOperator.SOFT_CLIP || ce.get(0).getOperator() == CigarOperator.HARD_CLIP) {
					ce.remove(0);
				}
				startClipLength = 0;
			} else {
				trimEnd = endClipLength;
				trimStart = 0;
				while (ce.get(ce.size() - 1).getOperator() == CigarOperator.SOFT_CLIP || ce.get(ce.size() - 1).getOperator() == CigarOperator.HARD_CLIP) {
					ce.remove(ce.size() - 1);
				}
				endClipLength = 0;
			}
			trimmed.setCigar(new Cigar(ce));
			trimmed.setBaseQualities(Arrays.copyOfRange(trimmed.getBaseQualities(), trimStart, trimmed.getReadLength() - trimEnd));
			trimmed.setReadBases(Arrays.copyOfRange(trimmed.getReadBases(), trimStart, trimmed.getReadLength() - trimEnd));
			read = trimmed;
		}
		// TODO: handle indels
		BitSet anchor = new BitSet(read.getReadLength());
		anchor.set(startClipLength, read.getReadLength() - (k - 1) - endClipLength, true);
		int startPosition;
		if (softClipEvidence.getBreakendSummary().direction == BreakendDirection.Forward) {
			startPosition = read.getAlignmentEnd() + endClipLength - read.getReadLength() + 1;
		} else {
			startPosition = read.getAlignmentStart() - startClipLength;
		}
		return new Evidence(softClipEvidence.getEvidenceID(), startPosition, startPosition, k, anchor, read.getReadBases(), read.getBaseQualities(), false, false);
	}
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Evidence other = (Evidence) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
