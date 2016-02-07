package ab.demo;

public class AHP {
	private String[] arr_criteria;
	private double[][] pairwise_matrix;
	private String[] arr_alt;
	private double[][] alt_matrix;

	public AHP (String[] arr_criteria, double[][] pairwise_matrix, String[] arr_alt, double[][] alt_matrix) {
		this.arr_criteria = arr_criteria;
		this.pairwise_matrix = pairwise_matrix;
		this.arr_alt = arr_alt;
		this.alt_matrix = alt_matrix;
	}

	public double[] compute() {
		int num_input = arr_criteria.length;

		double norm_matrix[] = new double[num_input];
		norm_matrix = ahpCriteria(arr_criteria, pairwise_matrix);

		return ahpAlternative(arr_alt, alt_matrix, norm_matrix);
	}

	// This aims to compute the eigenvector that gives us the relative ranking of our criteria

	public static double[] ahpCriteria(String arr_criteria[], double pairwise_matrix[][]) {
		int num_criteria = arr_criteria.length;
		double norm_matrix[] = new double[num_criteria];
		double res_matrix[][] = new double[num_criteria][num_criteria];
		double prev_matrix[] = new double[num_criteria];

		for (int i = 0; i<num_criteria; i++) {
			prev_matrix[i] = 0.0;
			for (int j = 0; j<num_criteria; j++) {
				res_matrix[i][j] = 0.0;
			}
		}

		Boolean test = true;
		while(test == true) {
			for (int i = 0; i<num_criteria; i++) {
				for (int j = 0; j<num_criteria; j++) {
					for(int k = 0; k<num_criteria; k++) {
						res_matrix[i][j] += pairwise_matrix[i][k] * pairwise_matrix[k][j];
					}
				}
			}
			// Normalization Step
			double sum = 0;
			for (int i = 0; i<num_criteria; i++) {
				norm_matrix[i] = 0.0;
				for (int j = 0; j< num_criteria; j++) {
					norm_matrix[i] += res_matrix[i][j];
				}
				sum += norm_matrix[i];
			}
			int count = 0;
			for (int i = 0; i<num_criteria; i++) {
				norm_matrix[i] = (double) (Math.round((norm_matrix[i]/sum)* 10000))/10000;
				if(Math.abs(norm_matrix[i] - prev_matrix[i]) <= 0.002) count++;
			}
			if (count == num_criteria) {
				test = false;
			}
			else {
				for (int i = 0; i<num_criteria; i++) {
					prev_matrix[i] = norm_matrix[i];
					for (int j = 0; j<num_criteria; j++) {
						pairwise_matrix[i][j] = res_matrix[i][j];
						res_matrix[i][j] = 0.0;
					}
				}
			}
		}
		return norm_matrix;
	}

	// This aims to compute the eigenvector that gives us the relative ranking of our alternatives
	public double[] ahpAlternative(String arr_alt[], double alt_matrix[][], double norm_matrix[]) {
		int num_alt = arr_alt.length;
		int num_criteria = alt_matrix[0].length;

		double result[] = new double[num_alt];

		for(int i = 0; i<num_alt; i++) {
			result[i] = 0.0;
		}

		for (int i = 0; i < num_alt; i++) {
			for(int j = 0; j < num_criteria; j++) {
				result[i] += alt_matrix[i][j] * norm_matrix[j];
			}
			result[i] = (double) (Math.round(result[i] * 10000)) / 10000;
		}
		return result;
	}
}
