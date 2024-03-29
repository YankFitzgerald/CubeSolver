package com.example.cubesolver;

import java.util.Arrays;

class Cubie {

    /**
     * 16 symmetries generated by S_F2, S_U4 and S_LR2
     */
    static Cubie[] CubeSymmetry = new Cubie[16];

    /**
     * 18 move cubes
     */
    static Cubie[] moveCube = new Cubie[18];

    static long[] moveCubeSymmetry = new long[18];
    static int[] firstMoveSymmetry = new int[48];

    static int[][] symmetryMult = new int[16][16];
    static int[][] symmetryMultInverse = new int[16][16];
    static int[][] symmetryMove = new int[16][18];
    static int[] symmetry8Move = new int[8 * 18];
    static int[][] symmetryMoveUD = new int[16][18];

    /**
     * ClassIndexToRepresentantArrays
     */
    static char[] FlipS2R = new char[Coordinate.NUM_FLIP_SYM];
    static char[] TwistS2R = new char[Coordinate.NUM_TWIST_SYM];
    static char[] EPermS2R = new char[Coordinate.NUM_PERM_SYM];
    static byte[] Perm2CombP = new byte[Coordinate.NUM_PERM_SYM];
    static char[] PermInvEdgeSym = new char[Coordinate.NUM_PERM_SYM];
    static byte[] MPermInv = new byte[Coordinate.NUM_MPERM];

    /**
     * Notice that Edge Perm Coordnate and Corner Perm Coordnate are the same symmetry structure.
     * So their ClassIndexToRepresentantArray are the same.
     * And when x is RawEdgePermCoordnate, y*16+k is SymEdgePermCoordnate, y*16+(k^e2c[k]) will
     * be the SymCornerPermCoordnate of the State whose RawCornerPermCoordnate is x.
     */
    // static byte[] e2c = {0, 0, 0, 0, 1, 3, 1, 3, 1, 3, 1, 3, 0, 0, 0, 0};
    static final int SYM_E2C_MAGIC = 0x00DDDD00;
    static int ESym2CSym(int idx) {
        return idx ^ (SYM_E2C_MAGIC >> ((idx & 0xf) << 1) & 3);
    }

    /**
     * Raw-Coordnate to Sym-Coordnate, only for speeding up initializaion.
     */
    static char[] FlipR2S = new char[Coordinate.NUM_FLIP];
    static char[] TwistR2S = new char[Coordinate.NUM_TWIST];
    static char[] EPermR2S = new char[Coordinate.NUM_PERM];
    static char[] FlipS2RF = Solver.USE_TWIST_FLIP_PRUN ? new char[Coordinate.NUM_FLIP_SYM * 8] : null;

    /**
     *
     */
    static char[] symmetryStateTwist;// = new char[Coordinate.N_TWIST_SYM];
    static char[] symmetryStateFlip;// = new char[Coordinate.N_FLIP_SYM];
    static char[] symmetryStatePerm;// = new char[Coordinate.N_PERM_SYM];

    // Cubie objects used to represent URF1 and URF2 symmetries.
    static Cubie urf1 = new Cubie(2531, 1373, 67026819, 1367);
    static Cubie urf2 = new Cubie(2089, 1906, 322752913, 2040);
    static byte[][] urfMove = new byte[][] {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17},
            {6, 7, 8, 0, 1, 2, 3, 4, 5, 15, 16, 17, 9, 10, 11, 12, 13, 14},
            {3, 4, 5, 6, 7, 8, 0, 1, 2, 12, 13, 14, 15, 16, 17, 9, 10, 11},
            {2, 1, 0, 5, 4, 3, 8, 7, 6, 11, 10, 9, 14, 13, 12, 17, 16, 15},
            {8, 7, 6, 2, 1, 0, 5, 4, 3, 17, 16, 15, 11, 10, 9, 14, 13, 12},
            {5, 4, 3, 8, 7, 6, 2, 1, 0, 14, 13, 12, 17, 16, 15, 11, 10, 9}
    };

    byte[] ca = {0, 1, 2, 3, 4, 5, 6, 7};   // corner array
    byte[] ea = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};    // edge array
    Cubie temps = null;

    Cubie() {
    }

    Cubie(int cperm, int twist, int eperm, int flip) {
        this.setCPerm(cperm);
        this.setTwist(twist);
        Util.setNPerm(ea, eperm, 12, true);
        this.setFlip(flip);
    }

    Cubie(Cubie c) {
        copy(c);
    }

    void copy(Cubie c) {
        for (int i = 0; i < 8; i++) {
            this.ca[i] = c.ca[i];
        }
        for (int i = 0; i < 12; i++) {
            this.ea[i] = c.ea[i];
        }
    }

    // Inverse the cube state
    void inverseCubieCube() {
        if (temps == null) {
            temps = new Cubie();
        }
        for (byte edge = 0; edge < 12; edge++) {
            temps.ea[ea[edge] >> 1] = (byte) (edge << 1 | ea[edge] & 1);
        }
        for (byte corn = 0; corn < 8; corn++) {
            temps.ca[ca[corn] & 0x7] = (byte) (corn | 0x20 >> (ca[corn] >> 3) & 0x18);
        }
        copy(temps);
    }

    /**
     * Calculate the corner permutation of a cubie cube.
     * prod = a * b, Corner Only.
     */
    static void cornerMult(Cubie a, Cubie b, Cubie prod) {
        for (int corn = 0; corn < 8; corn++) {
            int oriA = a.ca[b.ca[corn] & 7] >> 3;
            int oriB = b.ca[corn] >> 3;
            prod.ca[corn] = (byte) (a.ca[b.ca[corn] & 7] & 7 | (oriA + oriB) % 3 << 3);
        }
    }

    /**
     * Calculate the corner permutation of a cubie cube. (Consider mirrored cases)
     * prod = a * b, Corner Only. With mirrored cases considered
     */
    static void cornerMultFull(Cubie a, Cubie b, Cubie prod) {
        for (int corn = 0; corn < 8; corn++) {
            int oriA = a.ca[b.ca[corn] & 7] >> 3;
            int oriB = b.ca[corn] >> 3;
            int ori = oriA + ((oriA < 3) ? oriB : 6 - oriB);
            ori = ori % 3 + ((oriA < 3) == (oriB < 3) ? 0 : 3);
            prod.ca[corn] = (byte) (a.ca[b.ca[corn] & 7] & 7 | ori << 3);
        }
    }

    /**
     * Calculate the edge permutation of a cubie cube.
     * prod = a * b, Edge Only.
     */
    static void edgeMult(Cubie a, Cubie b, Cubie prod) {
        for (int ed = 0; ed < 12; ed++) {
            prod.ea[ed] = (byte) (a.ea[b.ea[ed] >> 1] ^ (b.ea[ed] & 1));
        }
    }

    /**
     * Update the corner permutation of a cubie cube.
     * b = S_idx^-1 * a * S_idx, Corner Only.
     */
    static void cornConjugate(Cubie a, int idx, Cubie b) {
        Cubie sinv = CubeSymmetry[symmetryMultInverse[0][idx]];
        Cubie s = CubeSymmetry[idx];
        for (int corn = 0; corn < 8; corn++) {
            int oriA = sinv.ca[a.ca[s.ca[corn] & 7] & 7] >> 3;
            int oriB = a.ca[s.ca[corn] & 7] >> 3;
            int ori = (oriA < 3) ? oriB : (3 - oriB) % 3;
            b.ca[corn] = (byte) (sinv.ca[a.ca[s.ca[corn] & 7] & 7] & 7 | ori << 3);
        }
    }

    /**
     * Update the edge permutation of a cubie cube.
     * b = S_idx^-1 * a * S_idx, Edge Only.
     */
    static void edgeConjugate(Cubie a, int idx, Cubie b) {
        Cubie sinv = CubeSymmetry[symmetryMultInverse[0][idx]];
        Cubie s = CubeSymmetry[idx];
        for (int ed = 0; ed < 12; ed++) {
            b.ea[ed] = (byte) (sinv.ea[a.ea[s.ea[ed] >> 1] >> 1] ^ (a.ea[s.ea[ed] >> 1] & 1) ^ (s.ea[ed] & 1));
        }
    }

    // Get the inverse of a permutation.
    static int getPermSymInv(int idx, int sym, boolean isCorner) {
        int idxi = PermInvEdgeSym[idx];
        if (isCorner) {
            idxi = ESym2CSym(idxi);
        }
        return idxi & 0xfff0 | symmetryMult[idxi & 0xf][sym];
    }

    // Get the moves that can be skipped.
    static int getSkipMoves(long ssym) {
        int ret = 0;
        for (int i = 1; (ssym >>= 1) != 0; i++) {
            if ((ssym & 1) == 1) {
                ret |= firstMoveSymmetry[i];
            }
        }
        return ret;
    }

    /**
     * Multiply the cube state by the inverse of the URF symmetry transformation.
     * this = S_urf^-1 * this * S_urf.
     */
    void URFConjugate() {
        if (temps == null) {
            temps = new Cubie();
        }
        cornerMult(urf2, this, temps);
        cornerMult(temps, urf1, this);
        edgeMult(urf2, this, temps);
        edgeMult(temps, urf1, this);
    }

    // ********************************************* Get and set coordinates *********************************************
    // XSym : Symmetry Coordnate of X. MUST be called after initialization of ClassIndexToRepresentantArrays.

    // ++++++++++++++++++++ Phase 1 Coordnates ++++++++++++++++++++
    // Flip : Orientation of 12 Edges. Raw[0, 2048) Sym[0, 336 * 8)
    // Twist : Orientation of 8 Corners. Raw[0, 2187) Sym[0, 324 * 8)
    // UDSlice : Positions of the 4 UDSlice edges, the order is ignored. [0, 495)

    int getFlip() {
        int idx = 0;
        for (int i = 0; i < 11; i++) {
            idx = idx << 1 | ea[i] & 1;
        }
        return idx;
    }

    void setFlip(int idx) {
        int parity = 0, val;
        for (int i = 10; i >= 0; i--, idx >>= 1) {
            parity ^= (val = idx & 1);
            ea[i] = (byte) (ea[i] & ~1 | val);
        }
        ea[11] = (byte) (ea[11] & ~1 | parity);
    }

    int getFlipSym() {
        return FlipR2S[getFlip()];
    }

    int getTwist() {
        int idx = 0;
        for (int i = 0; i < 7; i++) {
            idx += (idx << 1) + (ca[i] >> 3);
        }
        return idx;
    }

    void setTwist(int idx) {
        int twst = 15, val;
        for (int i = 6; i >= 0; i--, idx /= 3) {
            twst -= (val = idx % 3);
            ca[i] = (byte) (ca[i] & 0x7 | val << 3);
        }
        ca[7] = (byte) (ca[7] & 0x7 | (twst % 3) << 3);
    }

    int getTwistSym() {
        return TwistR2S[getTwist()];
    }

    int getUDSlice() {
        return 494 - Util.getComb(ea, 8, true);
    }

    void setUDSlice(int idx) {
        Util.setComb(ea, 494 - idx, 8, true);
    }

    // ++++++++++++++++++++ Phase 2 Coordnates ++++++++++++++++++++
    // EPerm : Permutations of 8 UD Edges. Raw[0, 40320) Sym[0, 2187 * 16)
    // Cperm : Permutations of 8 Corners. Raw[0, 40320) Sym[0, 2187 * 16)
    // MPerm : Permutations of 4 UDSlice Edges. [0, 24)

    int getCPerm() {
        return Util.getNPerm(ca, 8, false);
    }

    void setCPerm(int idx) {
        Util.setNPerm(ca, idx, 8, false);
    }

    int getCPermSym() {
        return ESym2CSym(EPermR2S[getCPerm()]);
    }

    int getEPerm() {
        return Util.getNPerm(ea, 8, true);
    }

    void setEPerm(int idx) {
        Util.setNPerm(ea, idx, 8, true);
    }

    int getEPermSym() {
        return EPermR2S[getEPerm()];
    }

    int getMPerm() {
        return Util.getNPerm(ea, 12, true) % 24;
    }

    void setMPerm(int idx) {
        Util.setNPerm(ea, idx, 12, true);
    }

    int getCComb() {
        return Util.getComb(ca, 0, false);
    }

    void setCComb(int idx) {
        Util.setComb(ca, idx, 0, false);
    }

    /**
     * Check a cubiecube for solvability. Return the error code.
     *  0: Cube is solvable
     * -2: Not all 12 edges exist exactly once
     * -3: Flip error: One edge has to be flipped
     * -4: Not all corners exist exactly once
     * -5: Twist error: One corner has to be twisted
     * -6: Parity error: Two corners or two edges have to be exchanged
     */
    int verify() {
        int sum = 0;
        int edgeMask = 0;
        for (int e = 0; e < 12; e++) {
            edgeMask |= 1 << (ea[e] >> 1);
            sum ^= ea[e] & 1;
        }
        if (edgeMask != 0xfff) {
            return -2;// missing edges
        }
        if (sum != 0) {
            return -3;
        }
        int cornMask = 0;
        sum = 0;
        for (int c = 0; c < 8; c++) {
            cornMask |= 1 << (ca[c] & 7);
            sum += ca[c] >> 3;
        }
        if (cornMask != 0xff) {
            return -4;// missing corners
        }
        if (sum % 3 != 0) {
            return -5;// twisted corner
        }
        if ((Util.getNParity(Util.getNPerm(ea, 12, true), 12) ^ Util.getNParity(getCPerm(), 8)) != 0) {
            return -6;// parity error
        }
        return 0;// cube ok
    }

    // Compute the symmetry of the cube.
    long selfSymmetry() {
        Cubie c = new Cubie(this);
        Cubie d = new Cubie();
        int cperm = c.getCPermSym() >> 4;
        long sym = 0L;
        for (int urfInv = 0; urfInv < 6; urfInv++) {
            int cpermx = c.getCPermSym() >> 4;
            if (cperm == cpermx) {
                for (int i = 0; i < 16; i++) {
                    cornConjugate(c, symmetryMultInverse[0][i], d);
                    if (Arrays.equals(d.ca, ca)) {
                        edgeConjugate(c, symmetryMultInverse[0][i], d);
                        if (Arrays.equals(d.ea, ea)) {
                            sym |= 1L << Math.min(urfInv << 4 | i, 48);
                        }
                    }
                }
            }
            c.URFConjugate();
            if (urfInv % 3 == 2) {
                c.inverseCubieCube();
            }
        }
        return sym;
    }

    // ********************************************* Initialization functions *********************************************

    static void initMove() {
        moveCube[0] = new Cubie(15120, 0, 119750400, 0);
        moveCube[3] = new Cubie(21021, 1494, 323403417, 0);
        moveCube[6] = new Cubie(8064, 1236, 29441808, 550);
        moveCube[9] = new Cubie(9, 0, 5880, 0);
        moveCube[12] = new Cubie(1230, 412, 2949660, 0);
        moveCube[15] = new Cubie(224, 137, 328552, 137);
        for (int a = 0; a < 18; a += 3) {
            for (int p = 0; p < 2; p++) {
                moveCube[a + p + 1] = new Cubie();
                edgeMult(moveCube[a + p], moveCube[a], moveCube[a + p + 1]);
                cornerMult(moveCube[a + p], moveCube[a], moveCube[a + p + 1]);
            }
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 8; i++) {
            sb.append("|" + (ca[i] & 7) + " " + (ca[i] >> 3));
        }
        sb.append("\n");
        for (int i = 0; i < 12; i++) {
            sb.append("|" + (ea[i] >> 1) + " " + (ea[i] & 1));
        }
        return sb.toString();
    }

    static void initSym() {
        Cubie c = new Cubie();
        Cubie d = new Cubie();
        Cubie t;

        Cubie f2 = new Cubie(28783, 0, 259268407, 0);
        Cubie u4 = new Cubie(15138, 0, 119765538, 7);
        Cubie lr2 = new Cubie(5167, 0, 83473207, 0);
        for (int i = 0; i < 8; i++) {
            lr2.ca[i] |= 3 << 3;
        }

        for (int i = 0; i < 16; i++) {
            CubeSymmetry[i] = new Cubie(c);
            cornerMultFull(c, u4, d);
            edgeMult(c, u4, d);
            t = d;  d = c;  c = t;
            if (i % 4 == 3) {
                cornerMultFull(c, lr2, d);
                edgeMult(c, lr2, d);
                t = d;  d = c;  c = t;
            }
            if (i % 8 == 7) {
                cornerMultFull(c, f2, d);
                edgeMult(c, f2, d);
                t = d;  d = c;  c = t;
            }
        }
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                cornerMultFull(CubeSymmetry[i], CubeSymmetry[j], c);
                for (int k = 0; k < 16; k++) {
                    if (Arrays.equals(CubeSymmetry[k].ca, c.ca)) {
                        symmetryMult[i][j] = k; // SymMult[i][j] = (k ^ i ^ j ^ (0x14ab4 >> j & i << 1 & 2)));
                        symmetryMultInverse[k][j] = i; // i * j = k => k * j^-1 = i
                        break;
                    }
                }
            }
        }
        for (int j = 0; j < 18; j++) {
            for (int s = 0; s < 16; s++) {
                cornConjugate(moveCube[j], symmetryMultInverse[0][s], c);
                for (int m = 0; m < 18; m++) {
                    if (Arrays.equals(moveCube[m].ca, c.ca)) {
                        symmetryMove[s][j] = m;
                        symmetryMoveUD[s][Util.std2ud[j]] = Util.std2ud[m];
                        break;
                    }
                }
                if (s % 2 == 0) {
                    symmetry8Move[j << 3 | s >> 1] = symmetryMove[s][j];
                }
            }
        }

        for (int i = 0; i < 18; i++) {
            moveCubeSymmetry[i] = moveCube[i].selfSymmetry();
            int j = i;
            for (int s = 0; s < 48; s++) {
                if (symmetryMove[s % 16][j] < i) {
                    firstMoveSymmetry[s] |= 1 << i;
                }
                if (s % 16 == 15) {
                    j = urfMove[2][j];
                }
            }
        }
    }

    static int initSym2Raw(final int N_RAW, char[] Sym2Raw, char[] Raw2Sym, char[] SymState, int coord) {
        final int N_RAW_HALF = (N_RAW + 1) / 2;
        Cubie c = new Cubie();
        Cubie d = new Cubie();
        int count = 0, idx = 0;
        int sym_inc = coord >= 2 ? 1 : 2;
        boolean isEdge = coord != 1;

        for (int i = 0; i < N_RAW; i++) {
            if (Raw2Sym[i] != 0) {
                continue;
            }
            switch (coord) {
                case 0: c.setFlip(i); break;
                case 1: c.setTwist(i); break;
                case 2: c.setEPerm(i); break;
            }
            for (int s = 0; s < 16; s += sym_inc) {
                if (isEdge) {
                    edgeConjugate(c, s, d);
                } else {
                    cornConjugate(c, s, d);
                }
                switch (coord) {
                    case 0: idx = d.getFlip();
                        break;
                    case 1: idx = d.getTwist();
                        break;
                    case 2: idx = d.getEPerm();
                        break;
                }
                if (coord == 0 && Solver.USE_TWIST_FLIP_PRUN) {
                    FlipS2RF[count << 3 | s >> 1] = (char) idx;
                }
                if (idx == i) {
                    SymState[count] |= 1 << (s / sym_inc);
                }
                int symIdx = (count << 4 | s) / sym_inc;
                Raw2Sym[idx] = (char) symIdx;
            }
            Sym2Raw[count++] = (char) i;
        }
        return count;
    }

    static void initFlipSym2Raw() {
        initSym2Raw(Coordinate.NUM_FLIP, FlipS2R, FlipR2S,
                symmetryStateFlip = new char[Coordinate.NUM_FLIP_SYM], 0);
    }

    static void initTwistSym2Raw() {
        initSym2Raw(Coordinate.NUM_TWIST, TwistS2R, TwistR2S,
                symmetryStateTwist = new char[Coordinate.NUM_TWIST_SYM], 1);
    }

    static void initPermSym2Raw() {
        initSym2Raw(Coordinate.NUM_PERM, EPermS2R, EPermR2S,
                symmetryStatePerm = new char[Coordinate.NUM_PERM_SYM], 2);
        Cubie cc = new Cubie();
        for (int i = 0; i < Coordinate.NUM_PERM_SYM; i++) {
            cc.setEPerm(EPermS2R[i]);
            Perm2CombP[i] = (byte) (Util.getComb(cc.ea, 0, true) + (Solver.USE_COMBP_PRUN ? Util.getNParity(EPermS2R[i], 8) * 70 : 0));
            cc.inverseCubieCube();
            PermInvEdgeSym[i] = (char) cc.getEPermSym();
        }
        for (int i = 0; i < Coordinate.NUM_MPERM; i++) {
            cc.setMPerm(i);
            cc.inverseCubieCube();
            MPermInv[i] = (byte) cc.getMPerm();
        }
    }

    static {
        Cubie.initMove();
        Cubie.initSym();
    }
}

