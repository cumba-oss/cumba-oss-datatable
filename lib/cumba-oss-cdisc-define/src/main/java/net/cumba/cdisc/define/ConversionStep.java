package net.cumba.cdisc.define;

import org.w3c.dom.Document;

/**
 * One atomic Define-XML upgrade step between two adjacent versions (e.g. 2.0 → 2.1). A
 * multi-version upgrade such as 1.0 → 2.1 is composed by {@link DefineXmlConverter} from the
 * consecutive atomic steps. Each step mutates the supplied DOM in place and records
 * progress/fidelity notes via the {@link ConversionContext}.
 */
interface ConversionStep
{

    DefineXmlConverter.Version from();


    DefineXmlConverter.Version to();


    /**
     * Apply the transformation in place on {@code doc}.
     */
    void apply(Document doc, ConversionContext ctx);

}
