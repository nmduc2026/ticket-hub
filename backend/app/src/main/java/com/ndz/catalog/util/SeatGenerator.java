package com.ndz.catalog.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sinh ghế của một khu {@code GRID} từ tham số (ADR 0030, overview.md §6.1). Bản Java của {@code generateSeats} ở
 * frontend: cùng tham số phải ra cùng toạ độ; hai bên dùng chung bộ dữ liệu {@code shared-test-data/generate-seats.json}.
 *
 * <ul>
 * <li>Ghế (hàng r, cột c) có toạ độ cục bộ {@code (c * seatGap, r * rowGap - curve * (1 - t²))}, với {@code t} chạy từ
 * -1 tới 1 dọc hàng (0 khi hàng chỉ có 1 ghế): hàng cong lên (y giảm) ở giữa một đoạn {@code curve}.</li>
 * <li>Xoay {@code rotation} độ theo chiều kim đồng hồ (trục y hướng xuống) quanh ghế đầu tiên, rồi dịch tới
 * {@code (x, y)}.</li>
 * <li>Nhãn hàng A, B, … Z, AA, AB, …; số ghế tăng từ trái sang phải ({@code LTR}) hoặc ngược lại ({@code RTL}).</li>
 * <li>Ghế ẩn (vd. {@code "A2"}) bị bỏ khỏi kết quả nhưng số của các ghế còn lại giữ nguyên.</li>
 * <li>Toạ độ làm tròn 2 chữ số thập phân.</li>
 * </ul>
 */
public final class SeatGenerator {

	private SeatGenerator() {
	}

	public record GridParams(double x, double y, double rotation, int rows, int seatsPerRow, double rowGap,
			double seatGap, double curve, boolean rightToLeft, List<String> hiddenSeats) {
	}

	public record GeneratedSeat(String rowLabel, int seatNumber, double x, double y) {
	}

	public static List<GeneratedSeat> generate(GridParams params) {
		Set<String> hidden = new HashSet<>(params.hiddenSeats());
		double theta = Math.toRadians(params.rotation());
		double cos = StrictMath.cos(theta);
		double sin = StrictMath.sin(theta);
		double half = (params.seatsPerRow() - 1) / 2.0;
		List<GeneratedSeat> seats = new ArrayList<>();
		for (int row = 0; row < params.rows(); row++) {
			String label = rowLabel(row);
			for (int column = 0; column < params.seatsPerRow(); column++) {
				int seatNumber = params.rightToLeft() ? params.seatsPerRow() - column : column + 1;
				if (hidden.contains(label + seatNumber)) {
					continue;
				}
				double t = params.seatsPerRow() == 1 ? 0 : (column - half) / half;
				double localX = column * params.seatGap();
				double localY = row * params.rowGap() - params.curve() * (1 - t * t);
				seats.add(new GeneratedSeat(label, seatNumber, round2(params.x() + localX * cos - localY * sin),
						round2(params.y() + localX * sin + localY * cos)));
			}
		}
		return seats;
	}

	/** A, B, …, Z, AA, AB, … (chỉ số bắt đầu từ 0). */
	public static String rowLabel(int index) {
		StringBuilder label = new StringBuilder();
		int rest = index + 1;
		while (rest > 0) {
			label.insert(0, (char) ('A' + (rest - 1) % 26));
			rest = (rest - 1) / 26;
		}
		return label.toString();
	}

	private static double round2(double value) {
		return Math.floor(value * 100 + 0.5) / 100 + 0.0; // + 0.0 đổi -0.0 thành 0.0
	}

}
