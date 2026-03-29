// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opendatahub.api.timeseries.ninja.utils.Representation;

public class UtilsTests {

	@Test
	public void testRepresentation() {
		Representation r = Representation.get("edge,flat");
		assertEquals(true, r.isEdge());
		assertEquals(true, r.isFlat());
		assertEquals(false, r.isNode());

		r = Representation.get("node,flat");
		assertEquals(false, r.isEdge());
		assertEquals(true, r.isFlat());
		assertEquals(true, r.isNode());

		r = Representation.get("node,tree");
		assertEquals(false, r.isEdge());
		assertEquals(false, r.isFlat());
		assertEquals(true, r.isNode());

		r = Representation.get("edge,tree");
		assertEquals(true, r.isEdge());
		assertEquals(false, r.isFlat());
		assertEquals(false, r.isNode());
	}

}
