package com.banasiak.coinflip.common

import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CoinGroup.CANADA
import com.banasiak.coinflip.common.CoinGroup.EURO
import com.banasiak.coinflip.common.CoinGroup.OTHER
import com.banasiak.coinflip.common.CoinGroup.US

/**
 * Where a coin comes from. The picker draws one header per group and only opens a new one on a
 * change of group, so [CoinType] has to stay sorted into unbroken runs of these -- a group that
 * reappears further down earns a second header rather than merging with the first.
 */
enum class CoinGroup(@param:StringRes val label: Int) {
  US(R.string.settings_item_coin_group_us),
  CANADA(R.string.settings_item_coin_group_canada),
  EURO(R.string.settings_item_coin_group_euro),
  OTHER(R.string.settings_item_coin_group_other)
}

/**
 * Every coin the app ships, in the order the picker lists them.
 *
 * [prefix] is the identity: it is the string SharedPreferences stores, and it names the artwork,
 * which `AnimationHelper` and the picker's thumbnails resolve at runtime as `<prefix>_heads` and
 * `<prefix>_tails`. Nothing in the compiler notices a prefix whose drawables are missing, so that
 * pairing is what `CoinResourcesTests` reads off disk to check.
 *
 * [coinName] is a plain string rather than a `@StringRes`. Eventually they should be moved into
 * String Resources and properly translated.
 */
enum class CoinType(val prefix: String, val coinName: String, val group: CoinGroup) {
  GEORGE_WASHINGTON("gw", "George Washington Dollar", US),
  ALABAMA("al", "Alabama Quarter", US),
  ALASKA("ak", "Alaska Quarter", US),
  ARIZONA("az", "Arizona Quarter", US),
  ARKANSAS("ar", "Arkansas Quarter", US),
  CALIFORNIA("ca", "California Quarter", US),
  COLORADO("co", "Colorado Quarter", US),
  CONNECTICUT("ct", "Connecticut Quarter", US),
  DELAWARE("de", "Delaware Quarter", US),
  DISTRICT_OF_COLUMBIA("dc", "District of Columbia Quarter", US),
  FLORIDA("fl", "Florida Quarter", US),
  GEORGIA("ga", "Georgia Quarter", US),
  HAWAII("hi", "Hawaii Quarter", US),
  IDAHO("id", "Idaho Quarter", US),
  ILLINOIS("il", "Illinois Quarter", US),
  INDIANA("in", "Indiana Quarter", US),
  IOWA("ia", "Iowa Quarter", US),
  KANSAS("ks", "Kansas Quarter", US),
  KENTUCKY("ky", "Kentucky Quarter", US),
  LOUISIANA("la", "Louisiana Quarter", US),
  MAINE("me", "Maine Quarter", US),
  MARYLAND("md", "Maryland Quarter", US),
  MASSACHUSETTS("ma", "Massachusetts Quarter", US),
  MICHIGAN("mi", "Michigan Quarter", US),
  MINNESOTA("mn", "Minnesota Quarter", US),
  MISSISSIPPI("ms", "Mississippi Quarter", US),
  MISSOURI("mo", "Missouri Quarter", US),
  MONTANA("mt", "Montana Quarter", US),
  NEBRASKA("ne", "Nebraska Quarter", US),
  NEVADA("nv", "Nevada Quarter", US),
  NEW_HAMPSHIRE("nh", "New Hampshire Quarter", US),
  NEW_JERSEY("nj", "New Jersey Quarter", US),
  NEW_MEXICO("nm", "New Mexico Quarter", US),
  NEW_YORK("ny", "New York Quarter", US),
  NORTH_CAROLINA("nc", "North Carolina Quarter", US),
  NORTH_DAKOTA("nd", "North Dakota Quarter", US),
  OHIO("oh", "Ohio Quarter", US),
  OKLAHOMA("ok", "Oklahoma Quarter", US),
  OREGON("or", "Oregon Quarter", US),
  PENNSYLVANIA("pa", "Pennsylvania Quarter", US),
  RHODE_ISLAND("ri", "Rhode Island Quarter", US),
  SOUTH_CAROLINA("sc", "South Carolina Quarter", US),
  SOUTH_DAKOTA("sd", "South Dakota Quarter", US),
  TENNESSEE("tn", "Tennessee Quarter", US),
  TEXAS("tx", "Texas Quarter", US),
  UTAH("ut", "Utah Quarter", US),
  VERMONT("vt", "Vermont Quarter", US),
  VIRGINIA("va", "Virginia Quarter", US),
  WASHINGTON("wa", "Washington Quarter", US),
  WEST_VIRGINIA("wv", "West Virginia Quarter", US),
  WISCONSIN("wi", "Wisconsin Quarter", US),
  WYOMING("wy", "Wyoming Quarter", US),
  JFK("jfk", "JFK Half-Dollar", US),
  SACAGAWEA("sacagawea", "Sacagawea Dollar", US),
  LOONIE("loonie", "Canadian Loonie", CANADA),
  TOONIE("toonie", "Canadian Toonie", CANADA),
  ANDORRA("andorra", "Andorra Euro", EURO),
  AUSTRIA("austria", "Austria Euro", EURO),
  BELGIUM("belgium", "Belgium Euro", EURO),
  CROATIA("croatia", "Croatia Euro", EURO),
  CYPRUS("cyprus", "Cyprus Euro", EURO),
  ESTONIA("estonia", "Estonia Euro", EURO),
  FINLAND("finland", "Finland Euro", EURO),
  FRANCE("france", "France Euro", EURO),
  GERMANY("germany", "Germany Euro", EURO),
  GREECE("greece", "Greece Euro", EURO),
  IRELAND("ireland", "Ireland Euro", EURO),
  ITALY("italy", "Italy Euro", EURO),
  LATVIA("latvia", "Latvia Euro", EURO),
  LITHUANIA("lithuania", "Lithuania Euro", EURO),
  LUXEMBOURG("luxembourg", "Luxembourg Euro", EURO),
  MALTA("malta", "Malta Euro", EURO),
  MONACO("monaco", "Monaco Euro", EURO),
  NETHERLANDS("netherlands", "Netherlands Euro", EURO),
  PORTUGAL("portugal", "Portugal Euro", EURO),
  SAN_MARINO("sanmarino", "San Marino Euro", EURO),
  SLOVAKIA("slovakia", "Slovakia Euro", EURO),
  SLOVENIA("slovenia", "Slovenia Euro", EURO),
  SPAIN("spain", "Spain Euro", EURO),
  VATICAN("vatican", "Vatican Euro", EURO),
  CLAUDE("claude", "Claude Code", OTHER),
  TWO_FACE("twoface", "Two-Face Dollar", OTHER),
  RANDOM("random", "Random Coin", OTHER);

  companion object {
    /** The coins that own artwork. [RANDOM] redraws from these on every flip rather than being one of them. */
    val flippable: List<CoinType> = CoinType.entries.filterNot { it == RANDOM }

    /** The coin a stored prefix names, or null when it names one this build no longer ships. */
    fun fromPrefix(prefix: String?): CoinType? = CoinType.entries.firstOrNull { it.prefix == prefix }
  }
}