package au.edu.wehi.idsv.sim;

import au.edu.wehi.idsv.GenomicProcessingContext;
import au.edu.wehi.idsv.vcf.SvType;
import com.google.common.collect.Lists;
import htsjdk.samtools.util.IOUtil;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;

import java.util.List;
import java.util.Locale;

@CommandLineProgramProperties(
        summary = "Create a fasta containing structural variants of the requested types. Can simulate, insertion of random sequence, deletion, inversion, and tandem duplication.",  
        oneLineSummary = "Simple structural variant simulator",
        programGroup = gridss.cmdline.programgroups.Benchmarking.class
)
public class GenerateSimpleVariants extends SimulationGenerator {
	public enum InsertionSequence {
		/**
		 * Complete random sequence with 50% GC bias
		 */
		Random,
		/**
		 * Sequence taken from random non-N location from the reference CHR
		 */
		Templated,
	}
    @Argument(doc="List of variants to insert. Valid variants are {INS, DEL, INV, DUP} for novel sequence insertion, deletion, inversion, and tandem duplication", optional=true)
    public List<SvType> TYPE = Lists.newArrayList(SvType.INS, SvType.DEL, SvType.INV, SvType.DUP);
    @Argument(doc="Variant sizes", optional=true)
	public List<Integer> SIZE = Lists.newArrayList(
	        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            12,
            16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64,
            72, 80,
            96, 112, 128,
            160, 192, 224, 256, 288, 320,
            512,
            1024,
            2048,
            4096);
            //8192,
            //16384,
            //32768,
            //65536);
    @Argument(doc="Number of copies of each variant (type,size) pairing to insert. Defaults to as many copies as possible ", optional=true)
    public Integer COPIES;
	@Argument(doc="Determines whether to output the variant as direct alternative sequence, or as a symbolic allele", optional=true)
    public boolean SYMBOLIC = true;
	@Argument(doc="Determines whether to output the variant as direct alternative sequence, or as a symbolic allele", optional=true)
	public InsertionSequence INSERTED_SEQUENCE;
	@Argument(doc="Determines which chromosome to sample inserted sequence from. Defaults to same as CHR", optional=true)
	public String INSERTED_SEQUENCE_CHR = null;
    @Override
	protected int doWork() {
    	try {
    		if (INSERTED_SEQUENCE_CHR == null) {
    			INSERTED_SEQUENCE_CHR = CHR;
			}
        	java.util.Locale.setDefault(Locale.ROOT);
        	IOUtil.assertFileIsReadable(REFERENCE_SEQUENCE);
        	GenomicProcessingContext pc = getProcessingContext();
        	SimpleVariantChromosome gen = new SimpleVariantChromosome(pc, CHR, UNAMBIGUOUS_MARGIN, RANDOM_SEED, SYMBOLIC, INSERTED_SEQUENCE_CHR);
        	gen.assemble(FASTA, VCF, BEDPE, INCLUDE_REFERENCE, TYPE, SIZE, COPIES == null ? Integer.MAX_VALUE : COPIES, INSERTED_SEQUENCE == InsertionSequence.Templated);
        } catch (Exception e) {
			e.printStackTrace();
			return 1;
		}
        return 0;
    }
	public static void main(String[] argv) {
        System.exit(new GenerateSimpleVariants().instanceMain(argv));
    }
}
