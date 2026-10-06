package com.pengunie.conversations;

public enum Grounding {

	/** Answer with at least one citation that checks out against the retrieved text. */
	GROUNDED,
	/** Nothing relevant was retrieved, or the model said the sources do not answer the question. */
	NOT_FOUND,
	/** The model claimed an answer but none of its citations could be verified; answer withheld. */
	UNVERIFIED

}
