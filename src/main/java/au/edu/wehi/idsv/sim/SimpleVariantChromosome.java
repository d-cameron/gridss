package au.edu.wehi.idsv.sim;

import au.edu.wehi.idsv.BreakendDirection;
import au.edu.wehi.idsv.BreakpointSummary;
import au.edu.wehi.idsv.GenomicProcessingContext;
import au.edu.wehi.idsv.bed.BedpeRecord;
import au.edu.wehi.idsv.bed.BedpeWriter;
import au.edu.wehi.idsv.sim.SequentialVariantPlacer.ContigExhaustedException;
import au.edu.wehi.idsv.vcf.SvType;
import com.google.common.io.Files;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.variant.variantcontext.VariantContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static au.edu.wehi.idsv.vcf.SvType.INS;

public class SimpleVariantChromosome extends SimulatedChromosome {
	private final boolean useSymbolicAllele;
	private final RandomBaseGenerator baseGen;
	private final RandomReferenceSequenceGenerator sequenceGen;
	/**
	 * @param margin number of unambiguous bases around the breakpoint
	 */
	public SimpleVariantChromosome(GenomicProcessingContext context, String chr, int margin, int seed, boolean useSymbolicAllele) {
		super(context, chr, margin, seed);
		this.baseGen = new RandomBaseGenerator(seed);
		this.sequenceGen = new RandomReferenceSequenceGenerator(seed, seq);
		this.useSymbolicAllele = useSymbolicAllele;
	}
	public SAMSequenceDictionary assemble(File fasta, File vcf, File breakpointPositionsBedpe, boolean includeReference, List<SvType> type, List<Integer> size, int countPerEventTypeSize, boolean templatedInsertions) throws IOException {
		List<SimpleEvent> variantList = populateEvents(type, size, countPerEventTypeSize, templatedInsertions);
		// ensure counts are the same
		variantList.subList(0, variantList.size() - variantList.size() % countPerEventTypeSize);
		variantList.sort(Comparator.comparingInt(o -> o.start));
		List<VariantContext> list = new ArrayList<VariantContext>();
		String variantContigName = "variant." + getChr();
		StringBuilder variantSeq = new StringBuilder();
		List<BedpeRecord> breakpointLocations = new ArrayList<>();

		int genomicPosition = 0; // emitted up to and including this genomic position
		for (SimpleEvent e : variantList) {
			String beforeVariantSequence = ref.getSubsequenceAt(chr, genomicPosition + 1, e.start).getBaseString();
			String variantSequence = e.getVariantSeq(ref, 0, 0);
			variantSeq.append(beforeVariantSequence);
			int positionBefore = variantSeq.length();
			variantSeq.append(variantSequence);
			int positionAfter = variantSeq.length();
			list.add(e.asVariantContextBuilder(ref, useSymbolicAllele).make());
			genomicPosition = e.start + e.getGenomicWidth();
			switch (e.type) {
				case INS:
				case DEL:
					breakpointLocations.add(new BedpeRecord(e.getID(ref), new BreakpointSummary(
							0, BreakendDirection.Forward, positionBefore,
							0, BreakendDirection.Backward, positionAfter + 1),
							Integer.toString(e.size)));
					break;
				case DUP:
					breakpointLocations.add(new BedpeRecord(e.getID(ref), new BreakpointSummary(
							0, BreakendDirection.Forward, positionBefore + e.size,
							0, BreakendDirection.Backward, positionBefore + e.size + 1),
							Integer.toString(e.size)));
					break;
				case INV:
					breakpointLocations.add(new BedpeRecord(e.getID(ref), new BreakpointSummary(
							0, BreakendDirection.Forward, positionBefore,
							0, BreakendDirection.Backward, positionBefore + 1),
							Integer.toString(e.size)));
					breakpointLocations.add(new BedpeRecord(e.getID(ref), new BreakpointSummary(
							0, BreakendDirection.Forward, positionAfter,
							0, BreakendDirection.Backward, positionAfter + 1),
							Integer.toString(e.size)));
					break;
				case BND:
				case CNV:
					throw new RuntimeException("NYI/Unreachable code detected");
			}
		}
		variantSeq.append(new String(seq, genomicPosition, margin));
		genomicPosition += margin;
		SAMSequenceDictionary dict = new SAMSequenceDictionary();
		dict.addSequence(new SAMSequenceRecord(variantContigName, genomicPosition));
		StringBuilder fsb = new StringBuilder();
		fsb.append(">" + variantContigName + "\n");
		fsb.append(variantSeq);
		if (includeReference) {
			fsb.append("\n>");
			fsb.append(getChr());
			fsb.append("\n");
			fsb.append(new String(seq, 0, genomicPosition));
			fsb.append("\n");
			dict.addSequence(new SAMSequenceRecord(getChr(), genomicPosition));
		}
		Files.asCharSink(fasta, StandardCharsets.US_ASCII).write(fsb.toString());
		//ReferenceCommandLineProgram.ensureSequenceDictionary(fasta);
		writeVcf(vcf, list);
		if (breakpointPositionsBedpe != null) {
			try (BedpeWriter writer = new BedpeWriter(dict, breakpointPositionsBedpe)) {
				writer.writeHeader(false, false);
				for (BedpeRecord r : breakpointLocations) {
					writer.write(r);
				}
			}
		}
		return dict;
	}
	private List<SimpleEvent> populateEvents(List<SvType> typeList, List<Integer> sizeList, int max, boolean templatedInsertions) {
		SequentialVariantPlacer placer = new SequentialVariantPlacer(seq, margin);
		List<SimpleEvent> variantList = new ArrayList<SimpleEvent>();
		try {
			for (int i = 0; i < max; i++) {
				for (int size : sizeList) {
					for (SvType type : typeList) {
						int width = SimpleEvent.getGenomicWidth(type, size);
						String insSeq = type != INS ? "" : new String((templatedInsertions ? sequenceGen : baseGen).getBases(size));
						variantList.add(new SimpleEvent(type, referenceIndex, size, placer.getNext(width + 1), insSeq));
					}
				}
			}
		} catch (ContigExhaustedException e) {
		}
		return variantList;
	}
}
