package dev.bbbt.suggest;

public interface PlacementValidator {

	boolean isFree(int x, int y, int z);

	boolean hasSupport(int x, int y, int z);

	PlacementValidator PERMISSIVE = new PlacementValidator() {
		@Override
		public boolean isFree(int x, int y, int z) {
			return true;
		}

		@Override
		public boolean hasSupport(int x, int y, int z) {
			return true;
		}
	};
}
